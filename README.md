# dquery-spring-boot-starter
dquery 动态查询的springboot自动配置starter方法
项目配置文件直接引入，在Repository接口方法中直接使用注解@DQuery，动态实现数据库原生SQL查询

###### 对应的使用事例项目
https://github.com/chuangchidong/dquery.git

###### 举例
项目下载编译，在自己的应用程序pom中加入引用，你懂得😉
```xml
     <dependency>
	<groupId>com.free.dquery</groupId>
	<artifactId>dquery-spring-boot-starter</artifactId>
	<version>0.0.1-SNAPSHOT</version>
    </dependency>
```

> 代码示例

```java
@DQuery(sqlHead = "select barcode,other_barcode from t_store_goods_other_barcode where is_deleted=0  ",
            dynamicSql = {
                    @DynamicSql(sql = " and barcode in (:barcodeList)",conditions = "barcodeList !=null "),
                    @DynamicSql(sql = " and store_id = :storeId",conditions = "storeId !=null && storeId > 0 "),
            })
List<GoodsBarcodeAndOtherCodeData> findGoodsOtherBarcodesByStoreIdAndBarcodeListStr(@Param("storeId") Long storeId, @Param("barcodeList") List<String> barcodeList);

```
在jpa的Repository的文件中使用 @DQuery动态查询；
> sqlHead 为查询SQL语句的主体部分

> dynamicSql 为SQL的拓展部分，根据条件动态增加

> @DynamicSql中sql为追加的查询SQL；conditions是根据方法的参数值判断拓展SQL是否追加到最终执行的SQL中去

> 查询的结果可自动转化为javabean对象，包括数组和分页数据，


###### 目的
> 解决JPA使用的关联表查询中，无法返回自定义的结果；解决JPA使用Specification、Criteria、@SqlResultSetMappings等繁琐操作来完成关联表操作

> 解决实际业务中查询条件不固定，业务代码需要写太多判断

###### 存在问题

> sessionFactory 配置版本较旧  
> springboot的版本需要换成2.0以上  

