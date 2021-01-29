package at.reisishot.bla.xml;

public class StringElement {

    private String string;

    public StringElement(final String string) {
        this();
        this.string = string;
    }

    public StringElement() {
    }

    public String getString() {
        return string;
    }

    public void setString(final String string) {
        this.string = string;
    }
}
