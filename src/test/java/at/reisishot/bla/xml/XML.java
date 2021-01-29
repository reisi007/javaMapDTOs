package at.reisishot.bla.xml;

import at.reisishot.bla.commons.ComplexType;

import java.util.List;

public class XML {

    private String b;

    private List<Integer> xmll;

    private List<StringElement> sE;

    private ComplexType xt;

    public String getB() {
        return b;
    }

    public void setB(String b) {
        this.b = b;
    }

    public ComplexType getXt() {
        return xt;
    }

    public void setXt(ComplexType xt) {
        this.xt = xt;
    }

    public List<Integer> getXmll() {
        return xmll;
    }

    public void setXmll(final List<Integer> xmll) {
        this.xmll = xmll;
    }

    public List<StringElement> getsE() {
        return sE;
    }

    public void setsE(final List<StringElement> sE) {
        this.sE = sE;
    }
}
