package io.quarkiverse.mybatis.plus.deployment;

import java.util.List;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.quarkiverse.mybatis.deployment.ConfigurationFactoryBuildItem;
import io.quarkiverse.mybatis.deployment.SqlSessionFactoryBuildItem;
import io.quarkiverse.mybatis.deployment.SqlSessionFactoryBuilderBuildItem;
import io.quarkiverse.mybatis.deployment.XMLConfigBuilderBuildItem;
import io.quarkiverse.mybatis.plus.MyBatisPlusConfig;
import io.quarkiverse.mybatis.plus.runtime.MyBatisPlusConfigurationFactory;
import io.quarkiverse.mybatis.plus.runtime.MyBatisPlusRecorder;
import io.quarkiverse.mybatis.plus.runtime.MyBatisPlusXMLConfigDelegateBuilder;
import io.quarkiverse.mybatis.runtime.meta.MapperDataSource;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

public class MyBatisPlusProcessor {

    private static final String FEATURE = "mybatis-plus";
    private static final DotName MYBATIS_PLUS_MAPPER = DotName.createSimple(BaseMapper.class.getName());
    private static final DotName MYBATIS_MAPPER_DATA_SOURCE = DotName.createSimple(MapperDataSource.class.getName());
    private static final DotName MYBATIS_PLUS_WRAPPER = DotName.createSimple(Wrapper.class.getName());
    private static final Logger LOG = Logger.getLogger(MyBatisPlusProcessor.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    ConfigurationFactoryBuildItem createConfigurationFactory() {
        return new ConfigurationFactoryBuildItem(new MyBatisPlusConfigurationFactory());
    }

    @BuildStep
    SqlSessionFactoryBuilderBuildItem createSqlSessionFactoryBuilder() {
        return new SqlSessionFactoryBuilderBuildItem(new MybatisSqlSessionFactoryBuilder());
    }

    @BuildStep
    XMLConfigBuilderBuildItem createXMLConfigBuilder() throws Exception {
        return new XMLConfigBuilderBuildItem(new MyBatisPlusXMLConfigDelegateBuilder());
    }

    @BuildStep
    void addDependencies(BuildProducer<IndexDependencyBuildItem> indexDependency) {
        indexDependency.produce(new IndexDependencyBuildItem("com.baomidou", "mybatis-plus-core"));
    }

    @BuildStep
    void reflectiveClasses(BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxyClass,
            CombinedIndexBuildItem indexBuildItem) {
        reflectiveClass.produce(new ReflectiveClassBuildItem(true, false,
                StatementHandler.class,
                Executor.class));
        reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, BoundSql.class));
        proxyClass.produce(new NativeImageProxyDefinitionBuildItem(StatementHandler.class.getName()));
        proxyClass.produce(new NativeImageProxyDefinitionBuildItem(Executor.class.getName()));

        for (AnnotationInstance i : indexBuildItem.getIndex().getAnnotations(DotName.createSimple(TableName.class.getName()))) {
            if (i.target().kind() == AnnotationTarget.Kind.CLASS) {
                DotName dotName = i.target().asClass().name();
                reflectiveClass.produce(new ReflectiveClassBuildItem(true, false, dotName.toString()));
            }
        }
        reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, MYBATIS_PLUS_WRAPPER.toString()));
        for (ClassInfo classInfo : indexBuildItem.getIndex().getAllKnownSubclasses(MYBATIS_PLUS_WRAPPER)) {
            reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, classInfo.name().toString()));
        }
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void init(List<SqlSessionFactoryBuildItem> sqlSessionFactoryBuildItems,
            MyBatisPlusConfig config,
            MyBatisPlusRecorder recorder) {
        sqlSessionFactoryBuildItems
                .forEach(sqlSessionFactory -> recorder.initSqlSession(sqlSessionFactory.getSqlSessionFactory(), config));
    }
}
