package com.kobylynskyi.graphql.codegen.mapper;

import com.kobylynskyi.graphql.codegen.model.MappingContext;
import com.kobylynskyi.graphql.codegen.model.ParameterDefinition;
import com.kobylynskyi.graphql.codegen.model.definitions.ExtendedDocument;
import com.kobylynskyi.graphql.codegen.model.definitions.ExtendedObjectTypeDefinition;
import com.kobylynskyi.graphql.codegen.model.definitions.ExtendedUnionTypeDefinition;
import com.kobylynskyi.graphql.codegen.utils.Utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.kobylynskyi.graphql.codegen.model.DataModelFields.BUILDER;
import static com.kobylynskyi.graphql.codegen.model.DataModelFields.CLASS_NAME;
import static com.kobylynskyi.graphql.codegen.model.DataModelFields.EQUALS_AND_HASH_CODE;
import static com.kobylynskyi.graphql.codegen.model.DataModelFields.FIELDS;
import static com.kobylynskyi.graphql.codegen.model.DataModelFields.IMMUTABLE_MODELS;
import static com.kobylynskyi.graphql.codegen.model.DataModelFields.IMPLEMENTS;
import static com.kobylynskyi.graphql.codegen.model.DataModelFields.JAVA_DOC;
import static com.kobylynskyi.graphql.codegen.model.DataModelFields.PACKAGE;
import static com.kobylynskyi.graphql.codegen.model.DataModelFields.TO_STRING;
import static com.kobylynskyi.graphql.codegen.model.DataModelFields.TO_STRING_FOR_REQUEST;

/**
 * Map type definition to a Freemarker data model
 *
 * @author kobylynskyi
 */
public class TypeDefinitionToDataModelMapper {

    private TypeDefinitionToDataModelMapper() {
    }

    /**
     * Map type definition to a Freemarker data model
     *
     * @param mappingContext Global mapping context
     * @param definition     Definition of object type including base definition and its extensions
     * @return Freemarker data model of the GraphQL type
     */
    public static Map<String, Object> map(MappingContext mappingContext,
                                          ExtendedObjectTypeDefinition definition) {
        ExtendedDocument document = mappingContext.getDocument();

        Map<String, Object> dataModel = new HashMap<>();
        // type/enum/input/interface/union classes do not require any imports
        dataModel.put(PACKAGE, MapperUtils.getModelPackageName(mappingContext));
        dataModel.put(CLASS_NAME, MapperUtils.getModelClassNameWithPrefixAndSuffix(mappingContext, definition));
        dataModel.put(JAVA_DOC, definition.getJavaDoc());
        dataModel.put(IMPLEMENTS, getInterfaces(mappingContext, definition));
        dataModel.put(FIELDS, getFields(mappingContext, definition, document));
        dataModel.put(BUILDER, mappingContext.getGenerateBuilder());
        dataModel.put(EQUALS_AND_HASH_CODE, mappingContext.getGenerateEqualsAndHashCode());
        dataModel.put(IMMUTABLE_MODELS, mappingContext.getGenerateImmutableModels());
        dataModel.put(TO_STRING, mappingContext.getGenerateToString());
        dataModel.put(TO_STRING_FOR_REQUEST, mappingContext.getGenerateClient());
        return dataModel;
    }

    /**
     * Get merged attributes from the type and attributes from the interface.
     *
     * @param mappingContext Global mapping context
     * @param typeDefinition GraphQL type definition
     * @param document       Parent GraphQL document
     * @return Freemarker data model of the GraphQL type
     */
    private static Collection<ParameterDefinition> getFields(MappingContext mappingContext,
                                                             ExtendedObjectTypeDefinition typeDefinition,
                                                             ExtendedDocument document) {
        // using the map to exclude duplicate fields from the type and interfaces
        Map<String, ParameterDefinition> allParameters = new LinkedHashMap<>();

        // includes parameters from the base definition and extensions
        FieldDefinitionToParameterMapper.mapFields(mappingContext, typeDefinition.getFieldDefinitions(), typeDefinition.getName())
                .forEach(p -> allParameters.put(p.getName(), p));
        // includes parameters from the interface
        MapperUtils.getInterfacesOfType(typeDefinition, document).stream()
                .map(i -> FieldDefinitionToParameterMapper.mapFields(mappingContext, i.getFieldDefinitions(), i.getName()))
                .flatMap(Collection::stream)
                .forEach(paramDef -> allParameters.merge(paramDef.getName(), paramDef, TypeDefinitionToDataModelMapper::merge));
        return allParameters.values();
    }

    /**
     * Merge parameter definition data from the type and interface
     * Annotations from the type have higher precedence
     *
     * @param typeDef      Definition of the same parameter from the type
     * @param interfaceDef Definition of the same parameter from the interface
     * @return merged parameter definition
     */
    private static ParameterDefinition merge(ParameterDefinition typeDef, ParameterDefinition interfaceDef) {
        if (Utils.isEmpty(typeDef.getAnnotations())) {
            typeDef.setAnnotations(interfaceDef.getAnnotations());
        }
        if (Utils.isEmpty(typeDef.getJavaDoc())) {
            typeDef.setJavaDoc(interfaceDef.getJavaDoc());
        }
        return typeDef;
    }

    private static Set<String> getInterfaces(MappingContext mappingContext,
                                             ExtendedObjectTypeDefinition definition) {
        List<String> unionsNames = mappingContext.getDocument().getUnionDefinitions()
                .stream()
                .filter(union -> union.isDefinitionPartOfUnion(definition))
                .map(ExtendedUnionTypeDefinition::getName)
                .map(unionName -> MapperUtils.getModelClassNameWithPrefixAndSuffix(mappingContext, unionName))
                .collect(Collectors.toList());
        Set<String> interfaceNames = definition.getImplements()
                .stream()
                .map(anImplement -> GraphqlTypeToJavaTypeMapper.getJavaType(mappingContext, anImplement))
                .collect(Collectors.toSet());

        Set<String> allInterfaces = new LinkedHashSet<>();
        allInterfaces.addAll(unionsNames);
        allInterfaces.addAll(interfaceNames);
        return allInterfaces;
    }

}
