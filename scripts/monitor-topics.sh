#!/bin/bash

echo "==========================================="
echo "Kafka Topic Monitor with kcat"
echo "==========================================="

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

NAMESPACE="debezium-pipeline"
KAFKA_POD=$(kubectl get pods -n $NAMESPACE -l app=kafka -o jsonpath='{.items[0].metadata.name}')

if [ -z "$KAFKA_POD" ]; then
    echo -e "${RED}Error: Kafka pod not found in namespace $NAMESPACE${NC}"
    exit 1
fi

# Function to check if kcat is installed in the Kafka pod
check_kcat() {
    kubectl exec -n $NAMESPACE $KAFKA_POD -- which kcat &>/dev/null
    if [ $? -ne 0 ]; then
        echo -e "${YELLOW}kcat not found in Kafka pod. Installing...${NC}"
        # Try to install kcat (this might not work in all Kafka images)
        kubectl exec -n $NAMESPACE $KAFKA_POD -- sh -c "
            if command -v apt-get &>/dev/null; then
                apt-get update && apt-get install -y kafkacat
            elif command -v apk &>/dev/null; then
                apk add --no-cache kafkacat
            elif command -v yum &>/dev/null; then
                yum install -y kafkacat
            else
                echo 'Package manager not found. Cannot install kcat.'
                exit 1
            fi
        " 2>/dev/null
        
        if [ $? -ne 0 ]; then
            echo -e "${RED}Unable to install kcat. Using kafka-console-consumer instead.${NC}"
            return 1
        fi
    fi
    return 0
}

# Function to list topics
list_topics() {
    echo -e "\n${BLUE}=== Available Kafka Topics ===${NC}"
    kubectl exec -n $NAMESPACE $KAFKA_POD -- \
        kafka-topics --bootstrap-server localhost:9092 --list
}

# Function to get topic metadata
topic_metadata() {
    local topic=$1
    echo -e "\n${BLUE}=== Topic Metadata: $topic ===${NC}"
    if check_kcat; then
        kubectl exec -n $NAMESPACE $KAFKA_POD -- \
            kcat -b localhost:9092 -t $topic -Q
    else
        kubectl exec -n $NAMESPACE $KAFKA_POD -- \
            kafka-topics --bootstrap-server localhost:9092 --describe --topic $topic
    fi
}

# Function to consume messages with kcat
consume_with_kcat() {
    local topic=$1
    local count=${2:-10}
    local offset=${3:-beginning}
    
    echo -e "\n${BLUE}=== Consuming from topic: $topic ===${NC}"
    echo -e "${YELLOW}Fetching last $count messages (from $offset)...${NC}\n"
    
    if check_kcat; then
        kubectl exec -n $NAMESPACE $KAFKA_POD -- \
            kcat -b localhost:9092 -t $topic -C -o $offset -c $count -f '\nKey: %k\nPartition: %p | Offset: %o | Timestamp: %T\nPayload: %s\n---\n'
    else
        # Fallback to kafka-console-consumer
        kubectl exec -n $NAMESPACE $KAFKA_POD -- timeout 10 \
            kafka-console-consumer --bootstrap-server localhost:9092 \
            --topic $topic --from-beginning --max-messages $count \
            --property print.timestamp=true \
            --property print.key=true \
            --property print.offset=true \
            --property print.partition=true || true
    fi
}

# Function to monitor topic in real-time
monitor_realtime() {
    local topic=$1
    
    echo -e "\n${BLUE}=== Real-time monitoring of topic: $topic ===${NC}"
    echo -e "${YELLOW}Press Ctrl+C to stop monitoring...${NC}\n"
    
    if check_kcat; then
        kubectl exec -n $NAMESPACE $KAFKA_POD -- \
            kcat -b localhost:9092 -t $topic -C -o end -f '\nKey: %k\nPartition: %p | Offset: %o | Timestamp: %T\nPayload: %s\n---\n'
    else
        kubectl exec -n $NAMESPACE $KAFKA_POD -- \
            kafka-console-consumer --bootstrap-server localhost:9092 \
            --topic $topic \
            --property print.timestamp=true \
            --property print.key=true \
            --property print.offset=true \
            --property print.partition=true
    fi
}

# Function to consume with JSON formatting
consume_json() {
    local topic=$1
    local count=${2:-10}
    
    echo -e "\n${BLUE}=== Consuming JSON messages from topic: $topic ===${NC}"
    echo -e "${YELLOW}Fetching last $count messages...${NC}\n"
    
    if check_kcat; then
        kubectl exec -n $NAMESPACE $KAFKA_POD -- \
            kcat -b localhost:9092 -t $topic -C -o beginning -c $count -f '%s\n' | \
            kubectl exec -i -n $NAMESPACE $KAFKA_POD -- sh -c "command -v jq >/dev/null && jq '.' || cat"
    else
        kubectl exec -n $NAMESPACE $KAFKA_POD -- timeout 10 \
            kafka-console-consumer --bootstrap-server localhost:9092 \
            --topic $topic --from-beginning --max-messages $count || true
    fi
}

# Function to show consumer group lag
show_lag() {
    local topic=$1
    
    echo -e "\n${BLUE}=== Consumer Group Lag for topic: $topic ===${NC}"
    kubectl exec -n $NAMESPACE $KAFKA_POD -- \
        kafka-consumer-groups --bootstrap-server localhost:9092 --all-groups --describe | grep $topic || echo "No consumer groups found for topic"
}

# Main menu
show_menu() {
    echo -e "\n${GREEN}=== Kafka Topic Monitor Options ===${NC}"
    echo "1) List all topics"
    echo "2) Show topic metadata"
    echo "3) Consume last N messages"
    echo "4) Monitor topic in real-time"
    echo "5) Consume with JSON formatting"
    echo "6) Show consumer group lag"
    echo "7) Monitor Debezium CDC topics"
    echo "8) Exit"
    echo -e "${YELLOW}Enter your choice:${NC} "
}

# Monitor Debezium topics specifically
monitor_debezium() {
    echo -e "\n${BLUE}=== Debezium CDC Topics ===${NC}"
    
    # List Debezium topics
    DEBEZIUM_TOPICS=$(kubectl exec -n $NAMESPACE $KAFKA_POD -- \
        kafka-topics --bootstrap-server localhost:9092 --list | grep -E "dbserver|debezium" || echo "")
    
    if [ -z "$DEBEZIUM_TOPICS" ]; then
        echo -e "${RED}No Debezium topics found${NC}"
        return
    fi
    
    echo -e "${GREEN}Found topics:${NC}"
    echo "$DEBEZIUM_TOPICS"
    
    echo -e "\n${YELLOW}Select a topic to monitor (or press Enter for 'dbserver1.public.customers'):${NC} "
    read selected_topic
    
    if [ -z "$selected_topic" ]; then
        selected_topic="dbserver1.public.customers"
    fi
    
    consume_json $selected_topic 10
}

# Check if arguments are provided
if [ $# -eq 0 ]; then
    # Interactive mode
    while true; do
        show_menu
        read choice
        
        case $choice in
            1)
                list_topics
                ;;
            2)
                echo -e "${YELLOW}Enter topic name:${NC} "
                read topic_name
                topic_metadata $topic_name
                ;;
            3)
                echo -e "${YELLOW}Enter topic name:${NC} "
                read topic_name
                echo -e "${YELLOW}Enter number of messages (default 10):${NC} "
                read msg_count
                msg_count=${msg_count:-10}
                consume_with_kcat $topic_name $msg_count
                ;;
            4)
                echo -e "${YELLOW}Enter topic name:${NC} "
                read topic_name
                monitor_realtime $topic_name
                ;;
            5)
                echo -e "${YELLOW}Enter topic name:${NC} "
                read topic_name
                echo -e "${YELLOW}Enter number of messages (default 10):${NC} "
                read msg_count
                msg_count=${msg_count:-10}
                consume_json $topic_name $msg_count
                ;;
            6)
                echo -e "${YELLOW}Enter topic name:${NC} "
                read topic_name
                show_lag $topic_name
                ;;
            7)
                monitor_debezium
                ;;
            8)
                echo -e "${GREEN}Exiting...${NC}"
                exit 0
                ;;
            *)
                echo -e "${RED}Invalid option${NC}"
                ;;
        esac
        
        echo -e "\n${YELLOW}Press Enter to continue...${NC}"
        read
    done
else
    # Command-line mode
    case $1 in
        list|ls)
            list_topics
            ;;
        metadata|meta|describe)
            topic_metadata $2
            ;;
        consume|read)
            consume_with_kcat $2 ${3:-10}
            ;;
        monitor|watch)
            monitor_realtime $2
            ;;
        json)
            consume_json $2 ${3:-10}
            ;;
        lag)
            show_lag $2
            ;;
        debezium|cdc)
            monitor_debezium
            ;;
        *)
            echo -e "${BLUE}Usage:${NC}"
            echo "  $0                           # Interactive mode"
            echo "  $0 list                      # List all topics"
            echo "  $0 metadata <topic>          # Show topic metadata"
            echo "  $0 consume <topic> [count]   # Consume N messages"
            echo "  $0 monitor <topic>           # Monitor in real-time"
            echo "  $0 json <topic> [count]      # Consume with JSON formatting"
            echo "  $0 lag <topic>               # Show consumer group lag"
            echo "  $0 debezium                  # Monitor Debezium CDC topics"
            ;;
    esac
fi
