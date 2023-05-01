package inspection;/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

import org.example.data.UnmappedTargetPropertiesData.Source;
import org.example.data.UnmappedTargetPropertiesData.Target;
import org.mapstruct.*;

interface NotMapStructMapper {

    Target map(Source source);
}

@Mapper
interface SingleMappingMapper {

    @Mapping(target = "testName", source = "name")
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.ERROR)
    Target <error descr="Unmapped target property: moreTarget">map</error>(Source source);
}

@Mapper
interface SingleMappingsMapper {

    @Mappings({
        @Mapping(target = "moreTarget", source = "moreSource")
    })
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.ERROR)
    Target <error descr="Unmapped target property: testName">map</error>(Source source);
}

@Mapper
interface SingleMappingsNoBracesMapper {

    @Mappings(
        @Mapping(target = "moreTarget", source = "moreSource")
    )
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.ERROR)
    Target <error descr="Unmapped target property: testName">map</error>(Source source);
}

@Mapper
interface NoMappingMapper {

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.ERROR)
    Target <error descr="Unmapped target properties: moreTarget, testName">map</error>(Source source);

    @org.mapstruct.InheritInverseConfiguration
    Source reverse(Target target);
}

@Mapper
interface NoMappingsMapper {

    @Mappings({
    })
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.ERROR)
    Target <error descr="Unmapped target properties: moreTarget, testName">map</error>(Source source);
}

@MapperConfig
interface AllMappingsMapperConfig {

    @Mappings({
        @Mapping(target = "testName", source = "name"),
        @Mapping(target = "moreTarget", source = "moreSource")
    })
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.ERROR)
    Target mapWithAllMappings(Source source);
}

@Mapper
interface UpdateMapper {

    @Mapping(target = "moreTarget", source = "moreSource")
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.ERROR)
    void <error descr="Unmapped target property: testName">update</error>(@MappingTarget Target target, Source source);
}

@Mapper
interface MultiSourceUpdateMapper {

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.ERROR)
    void <error descr="Unmapped target property: moreTarget">update</error>(@MappingTarget Target moreTarget, Source source, String testName, @org.mapstruct.Context String matching);
}

@Mapper
interface SingleMappingConstantReferenceMapper {

    String TEST_NAME = "testName";

    @Mapping(target = TEST_NAME, source = "name")
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.ERROR)
    Target <error descr="Unmapped target property: moreTarget">map</error>(Source source);
}