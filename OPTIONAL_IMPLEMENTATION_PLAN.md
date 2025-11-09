# Optional: Multiple Invocations per Client

## Core Idea

Allow each client to submit multiple operations sequentially. Each invocation is identified by `(clientId, invocationNum)`. **Key constraint**: Invocation N can only be proposed after invocation N-1 has been decided, ensuring strict FIFO order per client.

## Implementation Strategy

## Implementation Strategy

### 1. Data Structure Changes
```java
// Store multiple invocations per client (sorted by invocationNum)
Map<Integer, TreeMap<Integer, QueueOperation>> clientRequests;

// Track next expected invocation number for each client
Map<Integer, Integer> clientNextInvocation;
```

### 2. Operation Selection Logic
```java
// Only propose invocation N if invocations 0..N-1 have been decided
QueueOperation choosePendingOperation() {
    for (clientId, invocations in clientRequests) {
        int nextExpected = clientNextInvocation.get(clientId);
        if (invocations.contains(nextExpected)) {
            return invocations.get(nextExpected);
        }
    }
    return null;
}
```

### 3. Update on Decision
```java
handleDecide(operation) {
    // After deciding invocation N
    clientNextInvocation[clientId] = N + 1;
    // Now invocation N+1 can be proposed
}
```

## Example
```
Client sends: [inv0: ENQUEUE(10)], [inv1: ENQUEUE(20)], [inv2: DEQUEUE]

Proposal order (even if received out-of-order):
1. inv0 proposed → decided → clientNextInvocation[client] = 1
2. inv1 proposed → decided → clientNextInvocation[client] = 2
3. inv2 proposed → decided → clientNextInvocation[client] = 3
```

This ensures strict FIFO ordering per client while allowing concurrent operations across different clients.
