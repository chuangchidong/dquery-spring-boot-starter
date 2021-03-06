package com.free.dquery.handle;

import com.free.dquery.annotation.DQuery;
import com.free.dquery.annotation.DynamicSql;
import com.free.dquery.exception.DQueryException;
import com.free.dquery.queryparam.QueryParam;
import com.free.dquery.queryparam.QueryParamList;
import com.free.dquery.util.PageInfo;
import com.free.dquery.util.PageResult;
import com.free.dquery.util.QueryUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.hibernate.SessionFactory;
import org.springframework.data.repository.query.Param;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.util.*;

/**
 * @author zhangzhidong
 * @date 2017/11/27
 */
public class DQueryHandler {

    /**
     * 访问session
     */
    private SessionFactory sessionFactory;
    /**
     * 动态查询
     */
    private DQuery dQuery;
    /**
     * 切点方法
     */
    private Method method;
    /**
     * 分页信息
     */
    private PageInfo pageInfo;

    private ScriptEngine engine;

    public DQueryHandler(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public Object handler(ProceedingJoinPoint pjp) throws Throwable {

        // 检查是否需要拦截
        if (!checkMethodIsNeedInterception(pjp)) {
            return pjp.proceed();
        }

        // 获取参数
        Map<String, Object> methodParameters = this.getMethodParameters(pjp.getArgs());
        // 逻辑表达式对应的字段值
        this.judgementValues(methodParameters);

        List queryParameters = this.queryParameters(methodParameters);

        // 获取SQL
        String sql = getSql();

        return query(queryParameters, sql, method);

    }

    /**
     * @param queryParameters
     * @param sql
     * @param method
     * @return
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    private Object query(List queryParameters, String sql, Method method) throws InstantiationException, IllegalAccessException, ClassNotFoundException, ParseException {
        Type[] types = null;

        // 获取返回值类型
        Class<?> returnType = method.getReturnType();
        // 获取指定方法的返回值泛型信息
        Type genericReturnType = method.getGenericReturnType();

        // 判断获取的类型是否是参数类型
        if (genericReturnType instanceof ParameterizedType) {
            // 强制转型为带参数的泛型类型
            types = ((ParameterizedType) genericReturnType).getActualTypeArguments();
        }

        if (returnType.isArray() || Collection.class.isAssignableFrom(returnType)) {
            if (types != null && types.length > 0) {
                returnType = Class.forName(types[0].getTypeName());
            }
            // 列表
            return QueryUtil.queryForList(sql, queryParameters, null, null, returnType, sessionFactory);
        } else if (returnType == PageResult.class) {
            // 分页
            Long total = QueryUtil.queryCountSize(sql, queryParameters, sessionFactory);
            List list;
            if (total != null && total.longValue() > 0) {
                if (types != null && types.length > 0) {
                    returnType = Class.forName(types[0].getTypeName());
                }
                list = QueryUtil.queryForList(sql, queryParameters, pageInfo.getPage(), pageInfo.getSize(), returnType, sessionFactory);
            } else {
                list = new ArrayList<>();
            }

            return new PageResult(pageInfo.getPage(), pageInfo.getSize(), total, list);
        } else {
            // 对象javabean
            return QueryUtil.queryForObject(sql, queryParameters, returnType, sessionFactory);
        }

    }

    /**
     * 检查是否需要拦截--同时设置method对象
     *
     * @param pjp
     * @return
     */
    private boolean checkMethodIsNeedInterception(ProceedingJoinPoint pjp) {
        // 获取接口
        Signature sign = pjp.getSignature();
        MethodSignature ms = (MethodSignature) sign;
        this.method = ms.getMethod();
        Annotation[] annotations = method.getAnnotations();
        for (Annotation annotation : annotations) {
            // 含有自定义的注解
            if (annotation instanceof DQuery) {
                dQuery = (DQuery) annotation;
                return true;
            }
        }
        return false;
    }

    public Object handler(Method method, Object[] args) throws Exception {
        this.method = method;
        Annotation[] annotations = method.getAnnotations();
        for (Annotation annotation : annotations) {
            // 含有自定义的注解
            if (annotation instanceof DQuery) {
                dQuery = (DQuery) annotation;
            }
        }

        // 获取参数
        Map<String, Object> methodParameters = this.getMethodParameters(args);
        // 逻辑表达式对应的字段值
        this.judgementValues(methodParameters);

        List queryParameters = this.queryParameters(methodParameters);

        // 获取SQL
        String sql = getSql();

        return query(queryParameters, sql, method);
    }

    /**
     * 获取参数值
     *
     * @param args
     * @return
     * @throws Exception
     */
    private Map<String, Object> getMethodParameters(Object[] args) throws Exception {
        Map map = new HashMap();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        if (args.length != parameterAnnotations.length) {
            throw new DQueryException("形参定义的@param 数量,和实参数量不一致");
        }

        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation instanceof Param) {
                    Param param = (Param) annotation;

                    map.put(param.value(), args[i]);

                    if (args[i] instanceof PageInfo) {
                        pageInfo = (PageInfo) args[i];
                    }
                }
            }
        }

        return map;
    }

    /**
     * 查询SQL
     *
     * @return
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @throws ScriptException
     */
    private String getSql() throws NoSuchFieldException, IllegalAccessException, ScriptException {
        StringBuilder sb = new StringBuilder();
        String sqlHead = dQuery.sqlHead().replace("select", "SELECT").replace("from", "FROM");

        if (StringUtils.isEmpty(sqlHead)) {
            throw new DQueryException("哦豁,SQL头部为空,查毛线呢,(提示:神说,遇到新的注解,先看看注释)");
        }
        sb.append(sqlHead);

        // 动态SQL语句
        String isAddSql;
        // 动态SQL是否添加逻辑表达式
        String conditions;
        //  判断逻辑表达式结果
        Boolean flag;
        // 动态添加
        DynamicSql[] dynamicSqls = dQuery.dynamicSql();
        for (DynamicSql dynamicSql : dynamicSqls) {
            isAddSql = dynamicSql.sql();
            conditions = dynamicSql.conditions();
            if (!StringUtils.isEmpty(isAddSql) && !StringUtils.isEmpty(conditions)) {
                flag = (Boolean) engine.eval(conditions);
                if (flag) {
                    sb.append(isAddSql);
                }
            }
        }

        //加上SQL 尾部
        sb.append(dQuery.sqlTail());

        return sb.toString();
    }

    /**
     * 获取请求参数
     *
     * @param methodParameters
     * @return
     * @throws IllegalAccessException
     */
    private List queryParameters(Map<String, Object> methodParameters) throws IllegalAccessException {
        List parameters = new ArrayList<>();
        for (Map.Entry<String, Object> entry : methodParameters.entrySet()) {


            if (entry.getValue() == null) {
                parameters.add(new QueryParam(entry.getKey(), entry.getValue()));
            } else if (entry.getValue() instanceof Number) {
                parameters.add(new QueryParam(entry.getKey(), entry.getValue()));
            } else if (entry.getValue() instanceof String) {
                parameters.add(new QueryParam(entry.getKey(), entry.getValue()));
            } else if (entry.getValue() instanceof List) {
                parameters.add(new QueryParamList(entry.getKey(), ((List) entry.getValue()).toArray()));
            } else {
                Class<?> clazz = entry.getValue().getClass();
                Field[] fields = clazz.getDeclaredFields();
                if (fields != null && fields.length > 0) {
                    for (Field field : fields) {
                        String key = entry.getKey().concat(".").concat(field.getName());
                        field.setAccessible(true);
                        Object target = field.get(entry.getValue());
                        if (null == target || StringUtils.isEmpty(target.toString())) {
                            continue;
                        }
                        if (target.getClass().isArray() || Collection.class.isAssignableFrom(target.getClass())) {
                            parameters.add(new QueryParamList(key, (Object[]) target));
                        } else {
                            parameters.add(new QueryParam(key, target));
                        }
                    }
                }
            }

        }
        return parameters;
    }

    /**
     * 判断逻辑表达式对应字段的值
     *
     * @param methodParameters
     */
    private void judgementValues(Map<String, Object> methodParameters) {
        ScriptEngineManager manager = new ScriptEngineManager();
        this.engine = manager.getEngineByName("JavaScript");

        if (!CollectionUtils.isEmpty(methodParameters)) {
            for (Map.Entry<String, Object> entry : methodParameters.entrySet()) {
                engine.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
