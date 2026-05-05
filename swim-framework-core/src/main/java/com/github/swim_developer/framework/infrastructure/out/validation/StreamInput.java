package com.github.swim_developer.framework.infrastructure.out.validation;

import org.w3c.dom.ls.LSInput;

import java.io.InputStream;
import java.io.Reader;


class StreamInput implements LSInput {

    
    private InputStream byteStream;

    
    private String systemId;

    
    StreamInput(InputStream byteStream, String systemId) {
        this.byteStream = byteStream;
        this.systemId = systemId;
    }

    
    @Override public InputStream getByteStream() { return byteStream; }

    
    @Override public void setByteStream(InputStream byteStream) { this.byteStream = byteStream; }

    
    @Override public String getSystemId() { return systemId; }

    
    @Override public void setSystemId(String systemId) { this.systemId = systemId; }

    
    @Override public String getPublicId() { return null; }

    
    @Override public void setPublicId(String publicId) {
        throw new UnsupportedOperationException("setPublicId not supported");
    }


    @Override public String getBaseURI() { return null; }


    @Override public void setBaseURI(String baseURI) {
        throw new UnsupportedOperationException("setBaseURI not supported");
    }


    @Override public String getEncoding() { return null; }


    @Override public void setEncoding(String encoding) {
        throw new UnsupportedOperationException("setEncoding not supported");
    }


    @Override public boolean getCertifiedText() { return false; }


    @Override public void setCertifiedText(boolean certifiedText) {
        throw new UnsupportedOperationException("setCertifiedText not supported");
    }


    @Override public Reader getCharacterStream() { return null; }


    @Override public void setCharacterStream(Reader characterStream) {
        throw new UnsupportedOperationException("setCharacterStream not supported");
    }


    @Override public String getStringData() { return null; }


    @Override public void setStringData(String stringData) {
        throw new UnsupportedOperationException("setStringData not supported");
    }
}
