package org.c19x.data.type;

public class Message {
    public String value;

    public Message(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "Message{" +
                "value='" + value + '\'' +
                '}';
    }
}
