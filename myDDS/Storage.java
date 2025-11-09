package myDDS;

abstract class Storage {
  public abstract String read(String key);
  public abstract void write(String key, String val);
}
