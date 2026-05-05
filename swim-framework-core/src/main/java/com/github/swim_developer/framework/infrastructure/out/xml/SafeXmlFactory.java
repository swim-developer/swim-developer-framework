package com.github.swim_developer.framework.infrastructure.out.xml;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

public final class SafeXmlFactory {

    private static final XMLInputFactory STAX_INSTANCE = createStaxFactory();
    private static final DocumentBuilderFactory DOM_INSTANCE = createDomFactory();
    private static final TransformerFactory XSLT_INSTANCE = createTransformerFactory();

    private SafeXmlFactory() {
    }

    public static XMLInputFactory xmlInputFactory() {
        return STAX_INSTANCE;
    }

    public static DocumentBuilder documentBuilder() throws ParserConfigurationException {
        return DOM_INSTANCE.newDocumentBuilder();
    }

    public static Transformer transformer() throws TransformerConfigurationException {
        return XSLT_INSTANCE.newTransformer();
    }

    private static XMLInputFactory createStaxFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, false);
        return factory;
    }

    private static DocumentBuilderFactory createDomFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException e) {
            factory.setExpandEntityReferences(false);
        }
        return factory;
    }

    private static TransformerFactory createTransformerFactory() {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalStylesheet", "");
        return factory;
    }
}
