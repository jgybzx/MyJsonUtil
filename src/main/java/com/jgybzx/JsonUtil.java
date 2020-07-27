package com.jgybzx;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author jgybzx
 * @date 2020/07/27 17:22
 * @description json工具类
 */
public class JsonUtil {

    /**
     * 初始化 json转换对象，默认使用timestamps时间进行格式化
     *
     * @return
     */
    public static ObjectMapper initObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        mapper.configure(DeserializationFeature.WRAP_EXCEPTIONS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        // 对timestamps时间进行格式化
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        DateFormat d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        mapper.setDateFormat(d);

        mapper.getSerializerProvider().setNullValueSerializer(new JsonSerializer<Object>() {
            @Override
            public void serialize(Object arg0, JsonGenerator arg1, SerializerProvider arg2) throws IOException, JsonProcessingException {
                arg1.writeString("");
            }
        });
        return mapper;
    }

    /**
     * json转为指定类型的对象，对timestamps时间进行格式化
     *
     * @param json
     * @param requiredType
     * @param <T>
     * @return
     */
    public static <T> T jsonToClass(String json, Class<T> requiredType) {
        ObjectMapper mapper = initObjectMapper();
        try {
            return mapper.readValue(json, requiredType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * json数据转map
     *
     * @param json
     * @return
     */
    public static Map<String, Object> jsonToMap(String json) {
        if (StringUtils.isEmpty(json)) {
            return new HashMap<String, Object>();
        }
        try {
            ObjectMapper mapper = initObjectMapper();
            return mapper.readValue(json, mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * json转指定类型的list
     *
     * @param json
     * @param requiredType
     * @param <T>
     * @return
     */
    public static <T> List<T> jsonToList(String json, Class<T> requiredType) {
        ObjectMapper mapper = initObjectMapper();
        try {
            return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(ArrayList.class, requiredType));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * json转List<Map<String,Object>
     *
     * @param json
     * @return
     */
    public static List<Map<String, Object>> jsonToListMap(String json) {
        ObjectMapper mapper = initObjectMapper();
        try {
            return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(ArrayList.class, Map.class));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * map转对象
     *
     * @param map
     * @param requiredType
     * @param <T>
     * @return
     */
    public static <T> T mapToClass(Map<String, Object> map, Class<T> requiredType) {
        String s = toJson(map);
        return jsonToClass(s, requiredType);
    }

    /**
     * List<Map<String,Object> 转 List<Object>
     *
     * @param mapList
     * @param requiredType
     * @param <T>
     * @return
     */
    public static <T> List<T> mapToListObj(List<Map<String, Object>> mapList, Class<T> requiredType) {
        try {
            List<T> list = new ArrayList<>();
            for (Map<String, Object> map : mapList) {
                T t = mapToClass(map, requiredType);
                list.add(t);
            }
            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * map转对象
     *
     * @param map
     * @param requiredType
     * @param <T>
     * @return
     */
    public static <T> T mapToObj(Map<String, Object> map, Class<T> requiredType) {
        try {
            T t = requiredType.newInstance();
            List<Field> classFields = getClassFields(requiredType);
            for (Field field : classFields) {
                Object obj = map.get(field.getName());
                // 判断map里边是否有该对象的属性，如果没有不操作
                if (obj == null) {
                    continue;
                }
                // 获取属性类型
                Class<?> type = field.getType();
                // 可以操作私有属性
                field.setAccessible(true);
                // 当是基本类型的时候，进行赋值操作
                if (type == List.class) {
                    Class<?> genericity = getFieldGenericity(field);
                    if (genericity == null || isBaseType(type)) {
                        field.set(t, obj);
                    } else {
                        field.set(t, mapToListObj((List<Map<String, Object>>) obj, genericity));
                    }
                } else if (isBaseType(type)) {
                    field.set(t, obj);
                } else {
                    if ((obj instanceof Map) || (obj instanceof HashMap)) {
                        Object o = mapToObj((Map) obj, type);
                        field.set(t, o);
                    } else {
                        field.set(t, obj);
                    }
                }
            }
            return t;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * java对象转json
     *
     * @param object
     * @return
     */
    public static String toJson(Object object) {
        if (object == null) {
            return "";
        }
        ObjectMapper mapper = initObjectMapper();
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取对象及其父类的属性
     *
     * @param cla
     * @return
     */
    private static List<Field> getClassFields(Class<?> cla) {
        List<Field> fieldList = new ArrayList<>();
        // 获取本类的属性
        Field[] declaredFields = cla.getDeclaredFields();
        fieldList.addAll(Arrays.asList(declaredFields));
        // 如果父类不是Object，获取父类的属性
        Class<?> superclass = cla.getSuperclass();
        if (superclass != Object.class) {
            fieldList.addAll(getClassFields(superclass));
        }
        return fieldList;
    }

    /**
     * 获取属性的泛型，List<T>
     *
     * @return
     */
    private static Class<?> getFieldGenericity(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType genericType1 = (ParameterizedType) field.getGenericType();
            return (Class<?>) genericType1.getActualTypeArguments()[0];

        }
        return null;
    }

    /**
     * 判断是不是以下数据类型
     *
     * @param type
     * @return
     */
    private static Boolean isBaseType(Class<?> type) {
        if (type == Integer.class ||
                type == String.class ||
                type == Double.class ||
                type == Float.class ||
                type == Date.class ||
                type == java.sql.Date.class ||
                type == HashMap.class ||
                type == Map.class
        ) {
            return true;
        }
        return false;
    }
}
