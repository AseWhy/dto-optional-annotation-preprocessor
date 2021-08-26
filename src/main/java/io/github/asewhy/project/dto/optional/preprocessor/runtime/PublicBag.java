package io.github.asewhy.project.dto.optional.preprocessor.runtime;

import java.util.*;

public class PublicBag {
    private final Map<String, Object> contains;

    public PublicBag(){
        this.contains = new HashMap<>();
    }

    public PublicBag(Map<String, Object> contains){
        this.contains = Objects.requireNonNullElseGet(contains, HashMap::new);
    }

    public void fill(Map<String, Object> fill) {
        fill.putAll(this.contains);
    }

    public PublicBag set(String key, Object value) {
        this.contains.put(key, value); return this;
    }

    public PublicBag remove(String key) {
        this.contains.remove(key); return this;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String key) {
        if(this.get(key) instanceof Collection) {
            return (List<T>) this.get(key);
        } else {
            var list = new ArrayList<T>();
            this.contains.put(key, list);
            return list;
        }
    }

    public List<String> getStringList(String key) {
        return getList(key);
    }

    public List<Long> getLongList(String key) {
        return getList(key);
    }

    public List<Integer> getIntList(String key) {
        return getList(key);
    }

    public List<Double> getDoubleList(String key) {
        return getList(key);
    }

    public Long getLong(String key) {
        return (Long) this.contains.get(key);
    }

    public String getString(String key) {
        return (String) this.contains.get(key);
    }

    public Integer getInt(String key) {
        return (Integer) this.contains.get(key);
    }

    public Double getDouble(String key) {
        return (Double) this.contains.get(key);
    }

    public Boolean getBoolean(String key) {
        return (Boolean) this.contains.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T cast) {
        return (T) this.contains.get(key);
    }

    public Object get(String key) {
        return this.contains.get(key);
    }
}
