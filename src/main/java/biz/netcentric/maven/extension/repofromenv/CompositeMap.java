package biz.netcentric.maven.extension.repofromenv;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * A composite immutable map consisting out of multiple backing maps.
 * The backing maps are queried in the given order for the requested key until one is found.
 *
 * @param <K>
 * @param <V>
 */
public class CompositeMap<K, V> implements Map<K, V> {

    private final List<Map<K,V>> maps;

    @SafeVarargs
    public CompositeMap(Map<K,V>... maps) {
        this.maps = Arrays.asList(maps);
    }

    @Override
    public int size() {
        return keySet().size();
    }

    @Override
    public boolean isEmpty() {
        for (Map<K,V> map : maps) {
            if (!map.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean containsKey(Object key) {
        for (Map<K,V> map : maps) {
            if (!map.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        for (Map<K,V> map : maps) {
            if (!map.containsValue(value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V get(Object key) {
        for (Map<K,V> map : maps) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException("This is a read-only map implementation");
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException("This is a read-only map implementation");
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException("This is a read-only map implementation");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("This is a read-only map implementation");
    }

    @Override
    public Set<K> keySet() {
        Set<K> mergedKeys = new HashSet<>();
        for (Map<K,V> map : maps) {
            mergedKeys.addAll(map.keySet());
        }
        return mergedKeys;
    }

    @Override
    public Collection<V> values() {
        throw new UnsupportedOperationException("Only direct get operation supported");
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException("Only direct get operation supported");
    }

}
