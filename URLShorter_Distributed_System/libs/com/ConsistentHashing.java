package libs.com;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConsistentHashing {

    private static volatile ConsistentHashing instance;

    private final NavigableMap<BigInteger, String> circle;
    private final Set<String> nodes;
    private static final int VNODE_CNT = 3;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private ConsistentHashing() {
        this.circle = new ConcurrentSkipListMap<>();
        this.nodes = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    public static ConsistentHashing getInstance() {
        if (instance == null) {
            synchronized (ConsistentHashing.class) {
                if (instance == null) {
                    instance = new ConsistentHashing();
                }
            }
        }
        return instance;
    }

    public BigInteger hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes());
            return new BigInteger(1, digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    // Add a node to the hash ring with virtual nodes
    public void addNode(String node) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < VNODE_CNT; i++) {
                String vnodeKey = node + "#" + i;
                BigInteger hash = hash(vnodeKey);
                circle.put(hash, node);
                System.out.println("Virtual Node added: " + vnodeKey + " with hash: " + hash);
            }
            nodes.add(node);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Clear the hash ring
    public void clear() {
        lock.writeLock().lock();
        try {
            circle.clear();
            nodes.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Remove a node from the hash ring along with its virtual nodes
    public void removeNode(String node) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < VNODE_CNT; i++) {
                String vnodeKey = node + "#" + i;
                BigInteger hash = hash(vnodeKey);
                circle.remove(hash);
                System.out.println("Virtual Node removed: " + vnodeKey + " with hash: " + hash);
            }
            nodes.remove(node);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Find the node where a given key should be stored or retrieved
    public String matchNode(String key) {
        lock.readLock().lock();
        try {
            if (circle.isEmpty()) {
                throw new IllegalStateException("No nodes available in the system");
            }

            BigInteger hash = hash(key);
            Map.Entry<BigInteger, String> entry = circle.ceilingEntry(hash);
            if (entry == null) {
                entry = circle.firstEntry();
            }
            String realNode = entry.getValue();
            System.out.println("Node found: " + realNode);
            return realNode;
        } finally {
            lock.readLock().unlock();
        }
    }

    // Get an iterator over the successor nodes from a given key
    public Iterator<String> getSuccessorNodes(String key) {
        lock.readLock().lock();
        try {
            if (circle.isEmpty()) {
                throw new IllegalStateException("No nodes available in the system");
            }
            BigInteger hash = hash(key);
            NavigableMap<BigInteger, String> tailMap = circle.tailMap(hash, true);
            List<String> successors = new ArrayList<>(tailMap.values());
            successors.addAll(circle.headMap(hash, false).values());
            return successors.iterator();
        } finally {
            lock.readLock().unlock();
        }
    }

    // get an iterator over the predecessor nodes 
    public Iterator<String> getPredecessorNodes(String key) {
        lock.readLock().lock();
        try {
            if (circle.isEmpty()) {
                throw new IllegalStateException("No nodes available in the system");
            }
            BigInteger hash = hash(key);
            NavigableMap<BigInteger, String> descendingCircle = circle.descendingMap();
    
            // Get nodes with hashes less than or equal to the key's hash
            NavigableMap<BigInteger, String> tailMap = descendingCircle.tailMap(hash, true);
            // Get nodes with hashes greater than the key's hash (wrapping around the circle)
            NavigableMap<BigInteger, String> headMap = descendingCircle.headMap(hash, false);
    
            List<String> predecessors = new ArrayList<>();
            predecessors.addAll(tailMap.values());
            predecessors.addAll(headMap.values());
    
            return predecessors.iterator();
        } finally {
            lock.readLock().unlock();
        }
    }

    // Get a set of all nodes in the hash ring
    public Set<String> getNodes() {
        lock.readLock().lock();
        try {
            return new HashSet<>(nodes);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean withinRange(String key, String hostStart, String hostEnd) {
        lock.readLock().lock();
        try {
            BigInteger keyHash = hash(key);
            BigInteger startHash = hash(hostStart);
            BigInteger endHash = hash(hostEnd);

            if (startHash.compareTo(endHash) < 0) {
                return keyHash.compareTo(startHash) > 0 && keyHash.compareTo(endHash) <= 0;
            } else if (startHash.equals(endHash)) {
                return true;
            } else {
                return keyHash.compareTo(startHash) > 0 || keyHash.compareTo(endHash) <= 0;
            }
        } finally {
            lock.readLock().unlock();
        }
    }

}
