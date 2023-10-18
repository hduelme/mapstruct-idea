/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at https://www.apache.org/licenses/LICENSE-2.0
 */

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.MappingTarget;

class Source {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

class Target {

    private String testName;

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }
}

@Mapper
interface SourceMappingMapper {

    @Mapping(target = "testName", source = "name")
    Target map(Source source);
}

@Mapper
interface ExpressionMappingsMapper {

    @Mappings({
        @Mapping(target = "testName", expression = "java(\"My name\")")
    })
    Target map(Source source);
}

@Mapper
interface ConstantMapper {

    @Mapping(target = "testName", constant = "My name")
    void update(@MappingTarget Target target, Source source);
}

@Mapper
interface IgnoreMappingMapper {

    @Mapping(target = "testName", ignore = true)
    Target map(Source source);
}
