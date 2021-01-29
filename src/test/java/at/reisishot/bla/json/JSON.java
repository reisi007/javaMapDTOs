package at.reisishot.bla.json;

import at.reisishot.bla.commons.ComplexType;

import java.util.List;

public class JSON {

    private int a;

    private ComplexType jt;

    private List<String> jsonList;

    private List<String> secondListString;

    public int getA() {
        return a;
    }

    public void setA(int a) {
        this.a = a;
    }

    public ComplexType getJt() {
        return jt;
    }

    public void setJt(ComplexType jt) {
        this.jt = jt;
    }

    public List<String> getJsonList() {
        return jsonList;
    }

    public void setJsonList(final List<String> jsonList) {
        this.jsonList = jsonList;
    }

    public List<String> getSecondListString() {
        return secondListString;
    }

    public void setSecondListString(final List<String> secondListString) {
        this.secondListString = secondListString;
    }
}
