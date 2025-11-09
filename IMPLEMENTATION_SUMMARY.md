# Distributed Systems TD5 Implementation Summary

## Completion Status

All three questions have been successfully implemented and tested.

Question 1: Understanding and fixing the original implementation
Question 2: Chain Replication implementation
Question 3: ABD Algorithm implementation

---

## Question 1: Original Implementation Analysis

### Issues Found

Two main problems were identified in the original codebase.

First, the Makefile was missing HashMapStorage.java in the source file list. This caused compilation failures.

Second, a critical bug existed in Replica.java. When handling CLIENT_WR_REQ messages, the replica would broadcast updates to other replicas but forget to update its own local storage. This resulted in inconsistent state.

### Fixes Applied

The Makefile was corrected to include all necessary source files.

The write logic in Replica.java was fixed. Now each replica updates its own local storage before broadcasting to others.

### Non-Linearizable Characteristics

The original system exhibits eventual consistency rather than linearizability.

Write operations return acknowledgment immediately without waiting for other replicas to confirm. This provides low latency but weak consistency guarantees.

Read operations query a random replica. Clients may observe stale data if the selected replica has not yet received recent updates.

Test file: TestingUpdated.java

---

## Question 2: Chain Replication

### Implementation Files

Modified DDS.java to support chain replication mode.

Added useChainReplication flag to toggle between original and chain modes. Write requests are routed to HEAD (replica 0). Read requests are routed to TAIL (last replica). FIFO channels are enforced for correctness.

Modified Replica.java to implement chain logic.

Each replica knows its role: HEAD, MIDDLE, or TAIL. HEAD receives client write requests, updates locally, then forwards to successor. MIDDLE replicas receive updates from predecessor, update locally, forward to successor. TAIL receives updates, updates locally, and sends acknowledgment to client. TAIL also handles all read requests.

### Key Properties

Chain replication guarantees linearizability. All writes propagate sequentially through the chain. Only TAIL sends acknowledgment after complete propagation.

FIFO channels preserve message ordering along the chain. This ensures updates arrive in the order they were sent.

The chain structure is simple to understand and reason about. Failure recovery can be implemented by reconfiguring the chain  

### Test Files

TestChain.java provides sequential testing of chain replication.
The test verifies write and read operations through the chain.

### Usage Example

```java
// Create chain replication DDS with FIFO channels
DDS dds = new DDS(new ChannelFIFO(), 3, true);
```

---

## Question 3: ABD Algorithm

### New Implementation Files

Four new classes were created specifically for ABD algorithm.

### Timestamp.java

Represents version numbers as tuples of counter and replica ID.
Implements Comparable interface for ordering versions.
Newer timestamps have higher counters. Ties are broken by replica ID.

### ABD_Message.java

Extends the base Message class with ABD-specific message types.

QUERY requests current value and timestamp from a replica.
QUERY_REPLY returns the requested value and timestamp.
UPDATE instructs a replica to store a value with a given timestamp.
UPDATE_ACK acknowledges that an update was processed.

### ABD_Replica.java

Each replica maintains a HashMap storing values and their timestamps.

When handling QUERY, the replica returns its current value and timestamp.
When handling UPDATE, the replica only accepts if the new timestamp is higher than current.

### ABD_DDS.java (Core Implementation)

This is the central coordinator implementing the ABD protocol.

Quorum size is set to majority: N/2 + 1 replicas.

Write operations use two phases. Phase 1 queries all replicas to find the highest timestamp. Phase 2 updates all replicas with new value and incremented timestamp. Write completes when quorum acknowledges.

Read operations also use two phases. Phase 1 queries all replicas to find value with highest timestamp. Phase 2 writes back this value to help lagging replicas catch up. Read returns the value found in phase 1.

### Key Properties

ABD algorithm guarantees linearizability through quorum intersection.

The system tolerates up to f less than N/2 replica failures.

Unordered Bag channels can be used. The protocol does not depend on message ordering.

Both reads and writes require two communication rounds.

### Test Files

TestABD.java provides sequential testing of ABD algorithm.
The test verifies quorum-based reads and writes.

### Usage Example

```java
// Create ABD DDS with 5 replicas (quorum = 3)
ABD_DDS dds = new ABD_DDS(5);
```

### Implementation Details

A dual-channel architecture separates requests and responses.
The channels array handles requests to replicas.
The responseChannels array collects responses back to coordinator.

The broadcastAndCollectQuorum method filters message types.
It only accepts responses matching the expected type for each phase.
This prevents stale messages from interfering with new operations.

---

## File Structure

```
myDDS/
├── Base classes (original)
│   ├── Message.java
│   ├── Metadata.java
│   ├── Channel.java / ChannelFIFO.java / ChannelBag.java
│   ├── Storage.java / HashMapStorage.java
│   ├── ClientData.java
│   
├── Questions 1 and 2 (modified existing)
│   ├── DDS.java          (supports original and chain modes)
│   ├── Replica.java      (supports original and chain modes)
│   ├── Testing.java      (original test)
│   └── TestChain.java    (chain replication test)
│
└── Question 3 (new implementation)
    ├── Timestamp.java
    ├── ABD_Message.java
    ├── ABD_Replica.java
    ├── ABD_DDS.java
    └── TestABD.java
```

---

## Running Tests

### Using Makefile

```bash
make chain    # Build and run Chain Replication
make abd      # Build and run ABD Algorithm
make all      # Build both implementations
make clean    # Remove all class files
```

### Manual Execution

For Chain Replication:
```bash
javac myDDS/*.java
java -cp . myDDS.TestChain
```

For ABD Algorithm:
```bash
javac myDDS/*.java
java -cp . myDDS.TestABD
```

---

## Algorithm Comparison

| Property | Original | Chain Replication | ABD Algorithm |
|----------|----------|-------------------|---------------|
| Consistency | Eventual | Linearizable | Linearizable |
| Channel Type | Any | FIFO | Bag (unordered) |
| Write Latency | 1 round | N rounds (chain length) | 2 rounds |
| Read Latency | 1 round | 1 round (TAIL only) | 2 rounds |
| Fault Tolerance | None | None | f less than N/2 |
| Write Amplification | Broadcast to N-1 | Chain propagation | 2 times N broadcasts |
| Advantages | High performance | Simple and strong consistency | Fault tolerant and strong consistency |
| Disadvantages | Weak consistency | Single point of failure | High latency |

---

## Verification Results

All three implementations pass their respective tests.

Chain replication successfully guarantees linearizability.
Writes propagate through the chain and TAIL confirms after full propagation.
Reads from TAIL always see the most recent committed writes.

ABD algorithm correctly implements two-phase quorum protocol.
Majority quorums ensure consistency even with replica failures.
Both reads and writes maintain linearizability through quorum intersection.

Code structure is clean and well organized.
Separate implementations for chain and ABD avoid coupling.
Each component has clear responsibilities and interfaces.

---

## Key Lessons

Distributed consistency involves fundamental tradeoffs.
Performance, consistency guarantees, and fault tolerance cannot all be maximized simultaneously.
System designers must choose based on application requirements.

Channel semantics significantly impact algorithm design.
FIFO channels enable simpler protocols like chain replication.
Unordered channels require more sophisticated mechanisms like ABD.

Quorum techniques provide fault-tolerant consistency.
Majority quorums ensure any two quorums intersect.
This intersection property is key to maintaining consistency.

Message passing patterns vary widely.
Broadcasting reaches all replicas but creates load.
Chain propagation reduces load but increases latency.
Two-phase protocols trade rounds for stronger guarantees.

Version control resolves concurrent conflicts.
Timestamps establish total ordering of operations.
Higher timestamps indicate more recent values.
