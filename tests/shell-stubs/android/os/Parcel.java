package android.os;
public class Parcel {
    public void writeString(String s) { }
    public void writeInt(int n) { }
    public int readInt() { throw new UnsupportedOperationException(); }
    public void enforceInterface(String s) { }
    public String[] createStringArray() { throw new UnsupportedOperationException(); }
    public void writeNoException() { }
}
