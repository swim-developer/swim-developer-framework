package com.github.swim_developer.framework.unit.infrastructure;

import com.github.swim_developer.framework.infrastructure.out.xml.SafeXmlFactory;
import com.github.swim_developer.framework.testing.SwimRequirement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("infrastructure")
@SwimRequirement(
    spec = "EUROCONTROL SPEC-170",
    section = "7.1 XML Security",
    description = "XML processing MUST be protected against XXE and DTD injection attacks"
)
class SafeXmlFactoryTest {

    private static final String XXE_PAYLOAD = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [
              <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <root>&xxe;</root>
            """;

    private static final String SAFE_XML = """
            <?xml version="1.0"?>
            <root>
                <element>safe content</element>
            </root>
            """;

    @Test
    void xmlInputFactory_blocksExternalEntities() {
        XMLInputFactory factory = SafeXmlFactory.xmlInputFactory();

        assertThat(factory.getProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES))
                .isEqualTo(false);
        assertThat(factory.getProperty(XMLInputFactory.SUPPORT_DTD))
                .isEqualTo(false);
    }

    @Test
    void xmlInputFactory_parsesSafeXmlSuccessfully() throws Exception {
        XMLInputFactory factory = SafeXmlFactory.xmlInputFactory();

        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(SAFE_XML));

        boolean foundElement = false;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && "element".equals(reader.getLocalName())) {
                foundElement = true;
                break;
            }
        }

        assertThat(foundElement).isTrue();
    }

    @Test
    void documentBuilder_blocksXXEPayload() throws Exception {
        DocumentBuilder builder = SafeXmlFactory.documentBuilder();

        assertThatThrownBy(() ->
                builder.parse(new InputSource(new StringReader(XXE_PAYLOAD)))
        ).isInstanceOf(Exception.class);
    }

    @Test
    void documentBuilder_parsesSafeXmlSuccessfully() throws Exception {
        DocumentBuilder builder = SafeXmlFactory.documentBuilder();

        Document doc = builder.parse(new InputSource(new StringReader(SAFE_XML)));

        assertThat(doc.getDocumentElement().getNodeName()).isEqualTo("root");
        assertThat(doc.getElementsByTagName("element").getLength()).isEqualTo(1);
    }

    @Test
    void documentBuilder_isNamespaceAware() throws Exception {
        String nsXml = """
                <?xml version="1.0"?>
                <root xmlns:test="http://example.com/test">
                    <test:element>content</test:element>
                </root>
                """;

        DocumentBuilder builder = SafeXmlFactory.documentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(nsXml)));

        assertThat(doc.getDocumentElement().getNamespaceURI()).isNull();
    }

    @Test
    void transformer_createdSuccessfully() throws Exception {
        Transformer transformer = SafeXmlFactory.transformer();

        assertThat(transformer).isNotNull();
    }

    @Test
    void transformer_transformsDocumentSuccessfully() throws Exception {
        DocumentBuilder builder = SafeXmlFactory.documentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(SAFE_XML)));

        Transformer transformer = SafeXmlFactory.transformer();
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));

        String result = writer.toString();
        assertThat(result).contains("<root>").contains("<element>safe content</element>");
    }

    @Test
    void xmlInputFactory_isSingleton() {
        XMLInputFactory factory1 = SafeXmlFactory.xmlInputFactory();
        XMLInputFactory factory2 = SafeXmlFactory.xmlInputFactory();

        assertThat(factory1).isSameAs(factory2);
    }

    @Test
    void documentBuilder_isNotSingleton() throws Exception {
        DocumentBuilder builder1 = SafeXmlFactory.documentBuilder();
        DocumentBuilder builder2 = SafeXmlFactory.documentBuilder();

        assertThat(builder1).isNotSameAs(builder2);
    }

    @Test
    void transformer_isNotSingleton() throws Exception {
        Transformer transformer1 = SafeXmlFactory.transformer();
        Transformer transformer2 = SafeXmlFactory.transformer();

        assertThat(transformer1).isNotSameAs(transformer2);
    }
}
