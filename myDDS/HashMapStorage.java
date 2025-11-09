package myDDS;

import java.util.*;

public class HashMapStorage extends Storage
{
  // the storage is an in-memory hashmap
  HashMap<String, String> state = new HashMap<String, String>();

  public HashMapStorage() {

  }

  public String read(String key) {
    String result = "UNDEF";
    if (state.containsKey(key)) {
      result = state.get(key);
    }
    return result;
  }

  public void write(String key, String val) {
    state.put(key,val);
  }

}
