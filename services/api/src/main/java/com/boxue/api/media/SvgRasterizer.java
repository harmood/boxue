package com.boxue.api.media;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Component
public class SvgRasterizer {

    private static final Set<String> DANGEROUS_ELEMENTS = Set.of(
        "script", "foreignobject", "iframe", "object", "embed", "audio", "video", "style"
    );

    public ProcessedImage rasterize(byte[] svgBytes, int maxDimension) {
        Document document = parseSecure(svgBytes);
        Element root = document.getDocumentElement();
        sanitizeAttributes(root);
        sanitize(root);
        if (root == null || !"svg".equals(localName(root))) {
            throw invalid("SVG 根元素不合法");
        }
        double[] dimensions = dimensions(root);
        double width = dimensions[0];
        double height = dimensions[1];
        double scale = Math.min(1.0, maxDimension / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        try {
            byte[] sanitized = serialize(document);
            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float) targetWidth);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, (float) targetHeight);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            transcoder.transcode(
                new TranscoderInput(new ByteArrayInputStream(sanitized)),
                new TranscoderOutput(output)
            );
            return new ProcessedImage(output.toByteArray(), "image/png", targetWidth, targetHeight);
        } catch (Exception ex) {
            throw invalid("SVG 渲染失败");
        }
    }

    private Document parseSecure(byte[] bytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        } catch (Exception ex) {
            throw invalid("SVG 内容不合法");
        }
    }

    private void sanitizeAttributes(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        List<String> remove = new ArrayList<>();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String attrName = attribute.getNodeName().toLowerCase(Locale.ROOT);
            String value = attribute.getNodeValue() == null ? "" : attribute.getNodeValue().trim();
            if (attrName.startsWith("on")
                || ((attrName.equals("href") || attrName.equals("xlink:href"))
                    && !value.startsWith("#") && !value.startsWith("data:image/"))
                || (attrName.equals("style") && value.toLowerCase(Locale.ROOT).contains("url("))) {
                remove.add(attribute.getNodeName());
            }
        }
        for (String attr : remove) {
            element.removeAttribute(attr);
        }
    }

    private void sanitize(Node node) {
        List<Node> children = new ArrayList<>();
        NodeList nodeList = node.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            children.add(nodeList.item(i));
        }
        for (Node child : children) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                String name = localName(element);
                if (DANGEROUS_ELEMENTS.contains(name)) {
                    node.removeChild(child);
                    continue;
                }
                sanitizeAttributes(element);
                sanitize(element);
            }
        }
    }

    private byte[] serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document), new StreamResult(output));
        return output.toByteArray();
    }

    private double[] dimensions(Element root) {
        double width = parseLength(root.getAttribute("width"));
        double height = parseLength(root.getAttribute("height"));
        if (width <= 0 || height <= 0) {
            String viewBox = root.getAttribute("viewBox");
            if (viewBox != null && !viewBox.isBlank()) {
                String[] parts = viewBox.trim().split("[\\s,]+");
                if (parts.length == 4) {
                    try {
                        width = Double.parseDouble(parts[2]);
                        height = Double.parseDouble(parts[3]);
                    } catch (NumberFormatException ignored) {
                        // fall through
                    }
                }
            }
        }
        return new double[] {width > 0 ? width : 300, height > 0 ? height : 150};
    }

    private double parseLength(String value) {
        if (value == null || value.isBlank() || value.contains("%")) {
            return -1;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
            .replace("px", "").replace("pt", "").replace("mm", "").replace("cm", "");
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String localName(Element element) {
        String name = element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
        int colon = name.indexOf(':');
        return (colon >= 0 ? name.substring(colon + 1) : name).toLowerCase(Locale.ROOT);
    }

    private MediaException invalid(String message) {
        return new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_SVG_INVALID", message);
    }
}
