package io.micrc.lib;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.main.JsonValidator;
import com.schibsted.spt.data.jslt.Expression;
import com.schibsted.spt.data.jslt.Parser;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class JsonUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final ObjectMapper OBJECT_NULL_MAPPER = new ObjectMapper();

    private static final JsonValidator JSON_VALIDATOR = JsonSchemaFactory.byDefault().getValidator();

    static {
        OBJECT_MAPPER.setSerializationInclusion(Include.NON_NULL);

        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        OBJECT_NULL_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		/*
		// 序列化和反序列化的时候针对浮点类型使用BigDecimal转换，避免精度损失和科学计数法
		OBJECT_MAPPER.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
		OBJECT_MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		SimpleModule simpleModule = new SimpleModule();
		simpleModule.addSerializer(Double.class, BigDecimalForDoubleSerializer.SINGLETON);
		OBJECT_MAPPER.registerModule(simpleModule);
		OBJECT_MAPPER.setSerializationInclusion(Include.NON_NULL);
		 */
    }

    public static String writeValueAsString(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static String writeValueAsStringRetainNull(Object object) {
        try {
            return OBJECT_NULL_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    public static <T> T write2Object(String json, TypeReference<T> typeReference) {
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static <T> T writeValueAsObject(String json, Class<T> targetClass) {
        try {
            return OBJECT_MAPPER.readValue(json, targetClass);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static <T> T writeValueAsObjectRetainNull(String json, Class<T> targetClass) {
        try {
            return OBJECT_NULL_MAPPER.readValue(json, targetClass);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static <T> List<T> writeValueAsList(String json, Class<T> targetClass) {
        try {
            CollectionType listType = OBJECT_MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, targetClass);
            return OBJECT_MAPPER.readValue(json, listType);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static <T> T writeObjectAsObject(Object object, Class<T> targetClass) {
        return writeValueAsObject(writeValueAsString(object), targetClass);
    }

    public static <T> List<T> writeObjectAsList(Object object, Class<T> targetClass) {
        return writeValueAsList(writeValueAsString(object), targetClass);
    }

    public static JsonNode readTree(Object obj) {
        try {
            if (obj instanceof String) {
                return OBJECT_NULL_MAPPER.readTree((String) obj);
            }
            return OBJECT_NULL_MAPPER.readTree(JsonUtil.writeValueAsString(obj));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static boolean validate(String string) {
        try {
            OBJECT_NULL_MAPPER.readTree(string);
            return true;
        } catch (JsonParseException e) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String transform(String jslt, Object object) {
//                Expression expression = Parser.compileString(jslt);
        Parser parser = new Parser(new StringReader(jslt)).withObjectFilter("true"); // 保留值为[],{},null的键
        Expression expression = parser.compile();
        JsonNode resultNode = expression.apply(JsonUtil.readTree(object));
        return JsonUtil.writeValueAsStringRetainNull(resultNode);
    }

    public static String transAndCheck(String jslt, String string, String openApi) {
        if (jslt.startsWith("null") || jslt.startsWith("{}")) {
            return jslt;
        }
        try {
            Expression expression = Parser.compileString(jslt);
            JsonNode resultNode = expression.apply(JsonUtil.readTree(string));
            String result = null;
            if (StringUtils.hasText(openApi)) {
                JsonNode openApiNode = JsonUtil.readTree(openApi);
                // schema
                JsonNode schemaNode = openApiNode.at("/paths")
                        .iterator().next().at("/post/requestBody/content")
                        .iterator().next().at("/schema");
                // components
                JsonNode componentsNode = openApiNode.at("/components");
                // 合并
                HashMap hashMap = writeValueAsObject(schemaNode.toString(), HashMap.class);
                hashMap.put("components", writeValueAsObject(componentsNode.toString(), Object.class));
                // 检查
                ProcessingReport processingMessages = JSON_VALIDATOR.validateUnchecked(JsonUtil.readTree(hashMap), resultNode);
                result = processingMessages.isSuccess() ? resultNode.toString() : null;
            } else {
                result = "null".equals(resultNode.toString()) || "{}".equals(resultNode.toString()) ? null : resultNode.toString();
            }
            return result;
        } catch (Exception e) {
            //
        }
        return null;
    }

    public static Boolean hasPath(String json, String path) {
        JsonNode jsonNode = readTree(json);
        String[] paths = path.split("/");
        boolean hasPath = false;
        for (String p : paths) {
            hasPath = jsonNode.has(p);
            if (hasPath) {
                jsonNode = jsonNode.get(p);
            }
        }
        return hasPath;
    }

    public static Object readPath(String json, String path) {
        try {
            JsonNode jsonNode = readTree(json).at(path);
            if (jsonNode instanceof ObjectNode) {
                return writeValueAsObject(jsonNode.toString(), Object.class);
            }
            if (jsonNode instanceof ArrayNode) {
                return writeValueAsObject(jsonNode.toString(), List.class);
            }
            if (jsonNode instanceof TextNode) {
                return jsonNode.textValue();
            }
            if (jsonNode instanceof BinaryNode) {
                return jsonNode.binaryValue();
            }
            if (jsonNode instanceof ShortNode) {
                return jsonNode.shortValue();
            }
            if (jsonNode instanceof IntNode) {
                return jsonNode.intValue();
            }
            if (jsonNode instanceof LongNode) {
                return jsonNode.longValue();
            }
            if (jsonNode instanceof BigIntegerNode) {
                return jsonNode.bigIntegerValue();
            }
            if (jsonNode instanceof DecimalNode) {
                return jsonNode.decimalValue();
            }
            if (jsonNode instanceof FloatNode) {
                return jsonNode.floatValue();
            }
            if (jsonNode instanceof DoubleNode) {
                return jsonNode.doubleValue();
            }
            if (jsonNode instanceof BooleanNode) {
                return jsonNode.booleanValue();
            }
            // 路径不存在，返回null
            if (jsonNode instanceof MissingNode || jsonNode instanceof NullNode) {
                return null;
            }
            throw new UnsupportedOperationException(jsonNode.getClass().getName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String patch(String original, String path, String value) {
        String patchCommand = "[{ \"op\": \"replace\", \"path\": \"{{path}}\", \"value\": {{value}} }]";

        try {
            String pathReplaced = patchCommand.replace("{{path}}", path);
            String valueReplaced = pathReplaced.replace("{{value}}", value);
            JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(valueReplaced));
            return JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(original)));
        } catch (IOException | JsonPatchException e) {
            throw new RuntimeException("patch fail... please check object...");
        }
    }

    public static String add(String original, String path, String value) {
        String patchCommand = "[{ \"op\": \"add\", \"path\": \"{{path}}\", \"value\": {{value}} }]";
        try {
            String pathReplaced = patchCommand.replace("{{path}}", path);
            String valueReplaced = pathReplaced.replace("{{value}}", value);
            JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(valueReplaced));
            return JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(original)));
        } catch (IOException | JsonPatchException e) {
            throw new RuntimeException("patch fail... please check object...");
        }
    }

    /**
     * 补充目标JSON中不存在的节点
     *
     * @param json          json
     * @param targetPath    targetPath
     * @return              result
     */
    public static String supplementNotExistsNode(String json, String targetPath) {
        String[] split = targetPath.split("/");
        String p = "";
        for (int i = 1; i < split.length; i++) {
            p = p + "/" + split[i];
            Object o = readPath(json, p);
            if (null == o) {
                json = add(json, p, "{}");
            }
        }
        return json;
    }
}