JFLAGS = -g -Xlint:deprecation -Xlint:unchecked

# Common source files
COMMON_SRCS = \
	myDDS/Message.java \
	myDDS/Metadata.java \
	myDDS/Channel.java \
	myDDS/ChannelFIFO.java \
	myDDS/ChannelBag.java \
	myDDS/Storage.java \
	myDDS/HashMapStorage.java \
	myDDS/ClientData.java

# Chain Replication source files
CHAIN_SRCS = $(COMMON_SRCS) \
	myDDS/Replica.java \
	myDDS/DDS.java \
	myDDS/TestChain.java

# ABD Algorithm source files
ABD_SRCS = $(COMMON_SRCS) \
	myDDS/Timestamp.java \
	myDDS/ABD_Message.java \
	myDDS/ABD_Replica.java \
	myDDS/ABD_DDS.java \
	myDDS/TestABD.java

# Multi-Paxos source files
PAXOS_SRCS = $(COMMON_SRCS) \
	myDDS/Replica.java \
	myDDS/DDS.java \
	myDDS/QueueOperation.java \
	myDDS/MultiPaxos_Message.java \
	myDDS/MultiPaxos_Replica.java \
	myDDS/MultiPaxos_DDS.java \
	myDDS/TestMultiPaxos.java

# Multi-Paxos test scenarios
PAXOS_TEST_SRCS = $(COMMON_SRCS) \
	myDDS/Replica.java \
	myDDS/DDS.java \
	myDDS/QueueOperation.java \
	myDDS/MultiPaxos_Message.java \
	myDDS/MultiPaxos_Replica.java \
	myDDS/MultiPaxos_DDS.java \
	myDDS/TestMultiPaxosScenarios.java

# Multi-Paxos verification tests
PAXOS_VERIFY_SRCS = $(COMMON_SRCS) \
	myDDS/Replica.java \
	myDDS/DDS.java \
	myDDS/QueueOperation.java \
	myDDS/MultiPaxos_Message.java \
	myDDS/MultiPaxos_Replica.java \
	myDDS/MultiPaxos_DDS.java \
	myDDS/TestMultiPaxosVerification.java

# Default target
default:
	@echo "  Distributed system - TD5"
	@echo ""
	@echo "Available commands:"
	@echo "  make chain    - Clean, build and run Chain Replication"
	@echo "  make abd      - Clean, build and run ABD Algorithm"
	@echo "  make paxos    - Clean, build and run Multi-Paxos (basic test)"
	@echo "  make test     - Clean, build and run Multi-Paxos (all scenarios)"
	@echo "  make verify   - Clean, build and run Multi-Paxos (with verification)"
	@echo "  make all      - Build all implementations"
	@echo "  make clean    - Remove all .class files"
	@echo ""

# Build and run Chain Replication
chain: clean
	@echo "Building Chain Replication..."
	@javac $(JFLAGS) $(CHAIN_SRCS)
	@echo "✓ Chain Replication compiled"
	@echo ""
	@java -cp . myDDS.TestChain

# Build and run ABD Algorithm
abd: clean
	@echo "Building ABD Algorithm..."
	@javac $(JFLAGS) $(ABD_SRCS)
	@echo "✓ ABD Algorithm compiled"
	@echo ""
	@java -cp . myDDS.TestABD

# Build and run Multi-Paxos
paxos: clean
	@echo "Building Multi-Paxos Distributed Queue..."
	@javac $(JFLAGS) $(PAXOS_SRCS)
	@echo "✓ Multi-Paxos compiled"
	@echo ""
	@java -cp . myDDS.TestMultiPaxos

# Build and run Multi-Paxos test scenarios
test: clean
	@echo "Building Multi-Paxos Test Scenarios..."
	@javac $(JFLAGS) $(PAXOS_TEST_SRCS)
	@echo "✓ Multi-Paxos Test Scenarios compiled"
	@echo ""
	@java -cp . myDDS.TestMultiPaxosScenarios

# Build and run Multi-Paxos verification tests
verify: clean
	@echo "Building Multi-Paxos Verification Tests..."
	@javac $(JFLAGS) $(PAXOS_VERIFY_SRCS)
	@echo "✓ Multi-Paxos Verification Tests compiled"
	@echo ""
	@java -cp . myDDS.TestMultiPaxosVerification

# Build both implementations
all: clean
	@echo "Building all implementations..."
	@javac $(JFLAGS) myDDS/*.java
	@echo "✓ All files compiled successfully"

# Clean all class files and jar files
clean:
	@echo "Cleaning all .class and .jar files..."
	@rm -f myDDS/*.class
	@rm -f *.jar
	@find . -type f -name "*.class" -delete 2>/dev/null || true
	@echo "✓ Clean complete"

# Help target
help: default

.PHONY: default chain abd paxos test verify all clean help
