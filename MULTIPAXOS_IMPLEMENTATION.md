# Multi-Paxos Implementation Overview

## Project Structure

This project implements a distributed queue using the Multi-Paxos consensus algorithm. The implementation is based on modifying files from the previous ABD lab.

## Key Components

### 1. Message Class (`MultiPaxos_Message.java`)

**Message Types:**
- `CLIENT_REQUEST` - Client submits queue operations
- `PREPARE` - Leader election (Phase 1)
- `PROMISE` - Acceptor response to prepare
- `PROPOSE` - Value proposal (Phase 2)
- `ACCEPT` - Acceptor accepts proposal
- `DECIDE` - Broadcast decision to all replicas
- `DEQUEUE_RESPONSE` - Send dequeue result to client

**Payload:**
- The payload is `QueueOperation` which represents enqueue/dequeue operations
- Enqueue operations contain an integer value
- Dequeue operations return the first element in the queue

### 2. Replica Class (`MultiPaxos_Replica.java`)

**Core State:**
- `log` - HashMap storing decided operations (key: log index, value: QueueOperation)
- `promisedRound` - Highest round promised per log index
- `acceptedRound` - Highest round accepted per log index
- `acceptedValue` - Accepted operation per log index
- `actualQueue` - Java Queue for executing operations

**Key Methods:**
- `handleClientRequest()` - Store client requests and trigger leader election
- `handlePrepare()` - Respond to prepare messages (Phase 1)
- `handlePromise()` - Collect promises and become leader
- `handlePropose()` - Accept or reject proposals (Phase 2)
- `handleAccept()` - Track accepts and broadcast decision when quorum reached
- `handleDecide()` - Store decision in log and execute operations
- `executeLog()` - Execute all consecutive operations in order

**Leader Election:**
- Leader of round `r` is replica with `id = r mod N`
- After decision, leader continues with probability 0.5 (using `random.nextInt(2)`)
- Timeout mechanism ensures progress if designated leader fails

**Execution:**
- Operations only execute when all previous log indices are filled
- Maintains FIFO queue semantics across all replicas

### 3. Communication Channels

**Client-to-DDS:** FIFO channels
- Clients broadcast operations to all replicas
- Ensures ordered delivery per client

**Replica-to-Replica:** Bag channels
- Unordered delivery, may have duplicates
- Handles Multi-Paxos messages (PREPARE, PROMISE, PROPOSE, ACCEPT, DECIDE)

## Building and Testing

### Makefile Commands

```bash
make paxos    # Build and run basic Multi-Paxos test
make test     # Build and run comprehensive test scenarios
make verify   # Build and run verification tests
make clean    # Remove all compiled files
```

### Test Files

**`TestMultiPaxos.java`:**
- Basic test with 3 replicas
- Demonstrates enqueue and dequeue operations
- Tests leader election and consensus

**`TestMultiPaxosScenarios.java`:**
- Multiple test scenarios with different replica counts
- Concurrent operations from multiple clients
- Edge cases (empty queue dequeues)

**`TestMultiPaxosVerification.java`:**
- Verifies log consistency across replicas
- Checks queue state consistency
- Validates consensus properties

## Implementation Notes

1. **Single Invocation per Client:** Each client submits one operation (basic version)
2. **Quorum:** Majority (N/2 + 1) required for both PROMISE and ACCEPT
3. **Log Indices:** Start from 0 and increment sequentially
4. **Previously Accepted Values:** Must be proposed if discovered during Phase 1
5. **Output Timing:** Dequeue results sent only after all previous operations executed

## Testing Approach

1. Start with basic enqueue/dequeue operations
2. Test with different numbers of replicas (3, 5)
3. Verify all replicas maintain identical logs
4. Check queue state consistency
5. Test concurrent client operations
6. Verify leader election and continuation logic
