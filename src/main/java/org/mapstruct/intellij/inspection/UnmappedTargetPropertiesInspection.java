/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.intellij.inspection;

import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.intellij.MapStructBundle;
import org.mapstruct.intellij.settings.ProjectSettings;
import org.mapstruct.intellij.util.MapStructVersion;
import org.mapstruct.intellij.util.MapstructUtil;
import org.mapstruct.intellij.util.TargetUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.codeInsight.AnnotationUtil.findAnnotation;
import static com.intellij.codeInsight.AnnotationUtil.getBooleanAttributeValue;
import static org.mapstruct.intellij.util.MapstructAnnotationUtils.addMappingAnnotation;
import static org.mapstruct.intellij.util.MapstructUtil.isInheritInverseConfiguration;
import static org.mapstruct.intellij.util.MapstructUtil.isMapper;
import static org.mapstruct.intellij.util.MapstructUtil.isMapperConfig;
import static org.mapstruct.intellij.util.SourceUtils.findAllSourceProperties;
import static org.mapstruct.intellij.util.TargetUtils.findAllSourcePropertiesForCurrentTarget;
import static org.mapstruct.intellij.util.TargetUtils.findAllTargetProperties;

/**
 * Inspection that checks if there are unmapped target properties.
 *
 * @author Filip Hrisafov
 */
public class UnmappedTargetPropertiesInspection extends InspectionBase {
    @NotNull
    @Override
    PsiElementVisitor buildVisitorInternal(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new MyJavaElementVisitor( holder, MapstructUtil.resolveMapStructProjectVersion( holder.getFile() ) );
    }

    private static class MyJavaElementVisitor extends JavaElementVisitor {
        private final ProblemsHolder holder;
        private final MapStructVersion mapStructVersion;

        private MyJavaElementVisitor(ProblemsHolder holder, MapStructVersion mapStructVersion) {
            this.holder = holder;
            this.mapStructVersion = mapStructVersion;
        }

        @Override
        public void visitMethod(PsiMethod method) {
            super.visitMethod( method );

            PsiType targetType = getTargetType( method );
            if ( targetType == null ) {
                return;
            }

            if ( isBeanMappingIgnoreByDefault( method ) ) {
                return;
            }
            ReportingPolicy reportingPolicy = getReportingPolicy( method );
            if (reportingPolicy == ReportingPolicy.IGNORE) {
                return;
            }


            Set<String> allTargetProperties = findAllTargetProperties( targetType, mapStructVersion, method );

            // find and remove all defined mapping targets
            Set<String> definedTargets = TargetUtils.findAllDefinedMappingTargets( method, mapStructVersion )
                    .map( MyJavaElementVisitor::getBaseTarget )
                    .collect( Collectors.toSet() );
            allTargetProperties.removeAll( definedTargets );

            if ( definedTargets.contains( "." ) ) {
                // If there is a defined current target then we need to remove all implicit mapped properties for
                // the target source

                Set<String> currentTargetSourceProperties =
                    findAllSourcePropertiesForCurrentTarget(
                    method,
                    mapStructVersion
                )
                    .collect( Collectors.toSet() );

                allTargetProperties.removeAll( currentTargetSourceProperties );
            }

            //TODO maybe we need to improve this by more granular extraction
            Set<String> sourceProperties = findAllSourceProperties( method );
            allTargetProperties.removeAll( sourceProperties );

            int missingTargetProperties = allTargetProperties.size();
            if ( missingTargetProperties > 0 ) {
                String messageKey = missingTargetProperties == 1 ? "inspection.unmapped.target.property" :
                    "inspection.unmapped.target.properties.list";
                String descriptionTemplate = MapStructBundle.message(
                    messageKey,
                    allTargetProperties.stream()
                        .sorted()
                        .collect( Collectors.joining( ", " ) )
                );
                List<UnmappedTargetPropertyFix> quickFixes = new ArrayList<>( missingTargetProperties * 2 + 1 );

                allTargetProperties.stream()
                    .sorted()
                    .flatMap( property -> Stream.of(
                        createAddIgnoreUnmappedTargetPropertyFix( method, property ),
                        createAddUnmappedTargetPropertyFix( method, property )
                    ) )
                    .forEach( quickFixes::add );

                if ( missingTargetProperties > 1 ) {
                    // If there is more than one add ignore all
                    quickFixes.add( createAddIgnoreAllUnmappedTargetPropertiesFix( method, allTargetProperties ) );
                }

                //noinspection ConstantConditions
                holder.registerProblem(
                    method.getNameIdentifier(),
                    descriptionTemplate,
                        (ReportingPolicy.ERROR == reportingPolicy ? ProblemHighlightType.ERROR :
                    ProblemHighlightType.WARNING),
                    quickFixes.toArray( UnmappedTargetPropertyFix.EMPTY_ARRAY )
                );
            }
        }

        @NotNull
        private static String getBaseTarget(@NotNull String target) {
            int dotIndex = target.indexOf( "." );
            if ( dotIndex > 0 ) {
                return target.substring( 0, dotIndex );
            }
            return target;
        }

        private static boolean isBeanMappingIgnoreByDefault(PsiMethod method) {
            PsiAnnotation beanMapping = findAnnotation( method, true, MapstructUtil.BEAN_MAPPING_FQN );
            if ( beanMapping != null ) {
                Boolean ignoreByDefault = getBooleanAttributeValue( beanMapping, "ignoreByDefault" );
                if ( ignoreByDefault != null ) {
                    return ignoreByDefault;
                }
            }

            return false;
        }

        /**
         * @param method the method to be used
         *
         * @return the target class for the inspection, or {@code null} if no inspection needs to be performed
         */
        @Nullable
        private static PsiType getTargetType(PsiMethod method) {
            if ( !method.getModifierList().hasModifierProperty( PsiModifier.ABSTRACT ) ) {
                return null;
            }

            if ( isInheritInverseConfiguration( method ) ) {
                return null;
            }
            PsiClass containingClass = method.getContainingClass();

            if ( containingClass == null
                || method.getNameIdentifier() == null
                || !( isMapper( containingClass ) || isMapperConfig( containingClass ) ) ) {
                return null;
            }
            return TargetUtils.getRelevantType( method );
        }
    }

    private static class UnmappedTargetPropertyFix extends LocalQuickFixOnPsiElement {

        private static final UnmappedTargetPropertyFix[] EMPTY_ARRAY = new UnmappedTargetPropertyFix[0];

        private final String myText;
        private final String myFamilyName;
        private final Supplier<Collection<PsiAnnotation>> myAnnotationSupplier;
        private final boolean myMoveCaretToEmptySourceAttribute;

        private UnmappedTargetPropertyFix(@NotNull PsiMethod modifierListOwner,
            @NotNull String text,
            @NotNull String familyName,
            @NotNull Supplier<Collection<PsiAnnotation>> annotationSupplier,
            boolean moveCaretToEmptySourceAttribute) {
            super( modifierListOwner );
            myText = text;
            myFamilyName = familyName;
            myAnnotationSupplier = annotationSupplier;
            myMoveCaretToEmptySourceAttribute = moveCaretToEmptySourceAttribute;
        }

        @NotNull
        @Override
        public String getText() {
            return myText;
        }

        @Nls
        @NotNull
        @Override
        public String getFamilyName() {
            return myFamilyName;
        }

        @Override
        public boolean isAvailable(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement,
            @NotNull PsiElement endElement) {
            if ( !startElement.isValid() ) {
                return false;
            }
            if ( !PsiUtil.isLanguageLevel5OrHigher( startElement ) ) {
                return false;
            }
            final PsiModifierListOwner myModifierListOwner = (PsiModifierListOwner) startElement;

            // e.g. PsiTypeParameterImpl doesn't have modifier list
            return myModifierListOwner.getModifierList() != null;
        }

        @Override
        public void invoke(@NotNull Project project,
            @NotNull PsiFile file,
            @NotNull PsiElement startElement,
            @NotNull PsiElement endElement) {
            PsiMethod mappingMethod = (PsiMethod) startElement;

            for ( PsiAnnotation annotation : myAnnotationSupplier.get() ) {
                addMappingAnnotation( project, mappingMethod, annotation, myMoveCaretToEmptySourceAttribute );
            }
        }

    }

    private static class UnmappedTargetPropertyFixAnnotationSupplier implements Supplier<Collection<PsiAnnotation>> {
        private final PsiMethod method;
        private final String target;

        private UnmappedTargetPropertyFixAnnotationSupplier(PsiMethod method, String target) {
            this.method = method;
            this.target = target;
        }

        @Override
        public Collection<PsiAnnotation> get() {
            String annotationText = ProjectSettings.isPreferSourceBeforeTargetInMapping( method.getProject() ) ?
                "@" + MapstructUtil.MAPPING_ANNOTATION_FQN + "(source = \"\", target = \"" + target + "\")" :
                "@" + MapstructUtil.MAPPING_ANNOTATION_FQN + "(target = \"" + target + "\", source = \"\")";
            return Collections.singleton( JavaPsiFacade.getElementFactory( method.getProject() )
                .createAnnotationFromText( annotationText, null ) );
        }
    }

    /**
     * Add unmapped property fix. Property fix that adds a {@link org.mapstruct.Mapping} annotation with the
     * given {@code target}
     *
     * @param method the method to which the property needs to be added
     * @param target the name of the target property
     *
     * @return the Local Quick fix
     */
    private static UnmappedTargetPropertyFix createAddUnmappedTargetPropertyFix(PsiMethod method, String target) {
        String message = MapStructBundle.message( "inspection.add.unmapped.target.property", target );
        return new UnmappedTargetPropertyFix(
            method,
            message,
            MapStructBundle.message( "intention.add.unmapped.target.property" ),
            new UnmappedTargetPropertyFixAnnotationSupplier( method, target ),
            true
        );
    }

    /**
     * Add ignore unmapped property fix. Property fix that adds a {@link org.mapstruct.Mapping} annotation that ignores
     * the given {@code target}
     *
     * @param method the method to which the property needs to be added
     * @param target the name of the target property
     *
     * @return the Local Quick fix
     */
    private static UnmappedTargetPropertyFix createAddIgnoreUnmappedTargetPropertyFix(PsiMethod method, String target) {
        String fqn = MapstructUtil.MAPPING_ANNOTATION_FQN;
        Supplier<Collection<PsiAnnotation>> annotationSupplier =
            () -> Collections.singleton( JavaPsiFacade.getElementFactory(
            method.getProject() )
            .createAnnotationFromText( "@" + fqn + "(target = \"" + target + "\", ignore= true)", null ) );
        String message = MapStructBundle.message( "inspection.add.ignore.unmapped.target.property", target );
        return new UnmappedTargetPropertyFix(
            method,
            message,
            MapStructBundle.message( "intention.add.ignore.unmapped.target.property" ),
            annotationSupplier,
            false
        );
    }

    /**
     * Add ignore all unmapped properties fix. Property fix that adds {@link org.mapstruct.Mapping} annotations that
     * ignores all the given {@code targets}
     *
     * @param method the method to which the property needs to be added
     * @param targetProperties the names of the target properties that should be ignored
     *
     * @return the Local Quick fix
     */
    private static UnmappedTargetPropertyFix createAddIgnoreAllUnmappedTargetPropertiesFix(PsiMethod method,
        Collection<String> targetProperties) {
        String fqn = MapstructUtil.MAPPING_ANNOTATION_FQN;
        Supplier<Collection<PsiAnnotation>> annotationSupplier = () -> {
            List<PsiAnnotation> annotations = new ArrayList<>( targetProperties.size() );
            targetProperties.stream()
                .sorted()
                .forEach( targetProperty -> annotations.add( JavaPsiFacade.getElementFactory( method.getProject() )
                    .createAnnotationFromText(
                        "@" + fqn + "(target = \"" + targetProperty + "\", ignore= true)",
                        null
                    ) ) );
            return annotations;
        };
        String message = MapStructBundle.message( "inspection.add.ignore.all.unmapped.target.properties" );
        return new UnmappedTargetPropertyFix(
            method,
            message,
            MapStructBundle.message( "intention.add.ignore.all.unmapped.target.properties" ),
            annotationSupplier,
            false
        );
    }

    @NotNull
    private static ReportingPolicy getReportingPolicy( @NotNull PsiMethod method ) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return ReportingPolicy.WARN;
        }
        PsiAnnotation mapperAnnotation = containingClass.getAnnotation( MapstructUtil.MAPPER_ANNOTATION_FQN );
        if (mapperAnnotation == null) {
            return ReportingPolicy.WARN;
        }

        PsiAnnotationMemberValue classAnnotationOverwrite =
                mapperAnnotation.findDeclaredAttributeValue( "unmappedTargetPolicy" );
        if (classAnnotationOverwrite != null) {
            return getReportingPolicyFromAnnotation( classAnnotationOverwrite );
        }
        return getReportingPolicyFromMapperConfig( mapperAnnotation );
    }

    @NotNull
    private static ReportingPolicy getReportingPolicyFromMapperConfig( @NotNull PsiAnnotation mapperAnnotation ) {
        PsiAnnotationMemberValue configAnnotation = mapperAnnotation.findDeclaredAttributeValue( "config" );
        if (!(configAnnotation instanceof PsiClassObjectAccessExpression)) {
            return ReportingPolicy.WARN;
        }
        PsiTypeElement config = ((PsiClassObjectAccessExpression) configAnnotation).getOperand();
        PsiType configType = config.getType();
        if (!(configType instanceof PsiClassReferenceType)) {
            return ReportingPolicy.WARN;
        }
        PsiClass configClass = ((PsiClassReferenceType) configType).resolve();

        if (configClass == null) {
            return ReportingPolicy.WARN;
        }
        PsiAnnotation mapperConfigAnnotation = configClass.getAnnotation( MapstructUtil.MAPPER_CONFIG_ANNOTATION_FQN );

        if (mapperConfigAnnotation == null) {
            return ReportingPolicy.WARN;
        }
        PsiAnnotationMemberValue configValue =
                mapperConfigAnnotation.findDeclaredAttributeValue( "unmappedTargetPolicy" );
        if (configValue == null) {
            return ReportingPolicy.WARN;
        }
        return getReportingPolicyFromAnnotation( configValue );
    }

    @NotNull
    private static ReportingPolicy getReportingPolicyFromAnnotation( @NotNull PsiAnnotationMemberValue configValue ) {
        switch (configValue.getText()) {
            case "ReportingPolicy.IGNORE":
                return ReportingPolicy.IGNORE;
            case "ReportingPolicy.ERROR":
                return ReportingPolicy.ERROR;
            default:
                return ReportingPolicy.WARN;
        }
    }

}
