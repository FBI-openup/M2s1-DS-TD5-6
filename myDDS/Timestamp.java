package myDDS;

/**
 * Timestamp for ABD algorithm: (counter, replicaId)
 * Used to track version of data values
 */
public class Timestamp implements Comparable<Timestamp> {
    public int counter;
    public int replicaId;

    public Timestamp(int counter, int replicaId) {
        this.counter = counter;
        this.replicaId = replicaId;
    }

    // Copy constructor
    public Timestamp(Timestamp other) {
        this.counter = other.counter;
        this.replicaId = other.replicaId;
    }

    @Override
    public int compareTo(Timestamp other) {
        // Compare by counter first, then by replicaId
        if (this.counter != other.counter) {
            return Integer.compare(this.counter, other.counter);
        }
        return Integer.compare(this.replicaId, other.replicaId);
    }

    public boolean isGreaterThan(Timestamp other) {
        return this.compareTo(other) > 0;
    }

    @Override
    public String toString() {
        return "(" + counter + "," + replicaId + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Timestamp))
            return false;
        Timestamp other = (Timestamp) obj;
        return this.counter == other.counter && this.replicaId == other.replicaId;
    }

    @Override
    public int hashCode() {
        return counter * 1000 + replicaId;
    }
}
