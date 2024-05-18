/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.intellij.inspection;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationParamListImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mapstruct.intellij.MapStructBundle;
import org.mapstruct.intellij.util.MapstructUtil;
import org.mapstruct.intellij.util.SourceUtils;

/**
 * Inspection that checks if inside a @Mapping at least one source property is defined
 *
 * @author hduelme
 */
public class NoSourcePropertyDefinedInspection extends MappingAnnotationInspectionBase {

    @Override
    void visitMappingAnnotation( @NotNull ProblemsHolder problemsHolder, @NotNull PsiAnnotation psiAnnotation,
                                 @NotNull MappingAnnotation mappingAnnotation ) {
        PsiNameValuePair targetProperty = mappingAnnotation.getTargetProperty();
        PsiMethod annotatedMethod = getAnnotatedMethod( psiAnnotation );
        if (targetProperty != null && annotatedMethod != null && mappingAnnotation.hasNoSourceProperties()
                && !isIgnoreByDefaultEnabled( annotatedMethod ) &&
                !hasMatchingSourceProperty( annotatedMethod, targetProperty ) ) {
            problemsHolder.registerProblem( psiAnnotation,
                    MapStructBundle.message( "inspection.no.source.property" ) );
        }
    }

    private boolean hasMatchingSourceProperty( @NotNull PsiMethod annotatedMethod,
                                               @NotNull PsiNameValuePair targetProperty ) {
        String targetValue = targetProperty.getLiteralValue();
        if (targetValue == null) {
            return false;
        }
        return  SourceUtils.findAllSourceProperties( annotatedMethod ).contains( targetValue );
    }

    private static boolean isIgnoreByDefaultEnabled( @NotNull PsiMethod annotatedMethod ) {
        PsiAnnotation beanMappingAnnotation = annotatedMethod.getAnnotation( MapstructUtil.BEAN_MAPPING_FQN );
        if (beanMappingAnnotation == null) {
            return false;
        }
        PsiAnnotationMemberValue ignoreByDefault =
                beanMappingAnnotation.findDeclaredAttributeValue( "ignoreByDefault" );
        return ignoreByDefault instanceof PsiLiteralExpression
                && Boolean.TRUE.equals( ((PsiLiteralExpression) ignoreByDefault).getValue() );
    }

    @Nullable
    private static PsiMethod getAnnotatedMethod(@NotNull PsiAnnotation psiAnnotation) {
        PsiElement psiAnnotationParent = psiAnnotation.getParent();
        if (psiAnnotationParent == null) {
            return null;
        }
        PsiElement psiAnnotationParentParent = psiAnnotationParent.getParent();
        if (psiAnnotationParentParent instanceof PsiMethod) {
            // directly annotated with @Mapping
            return (PsiMethod) psiAnnotationParentParent;
        }

        PsiElement psiAnnotationParentParentParent = psiAnnotationParentParent.getParent();
        if (psiAnnotationParentParentParent instanceof PsiAnnotation) {
            // inside @Mappings without array
            PsiElement mappingsAnnotationParent = psiAnnotationParentParentParent.getParent();
            if (mappingsAnnotationParent == null) {
                return null;
            }
            PsiElement mappingsAnnotationParentParent = mappingsAnnotationParent.getParent();
            if (mappingsAnnotationParentParent instanceof PsiMethod) {
                return (PsiMethod) mappingsAnnotationParentParent;
            }
            return null;
        }
        else if (psiAnnotationParentParentParent instanceof PsiAnnotationParamListImpl) {
            // inside @Mappings wit array
            PsiElement mappingsArray = psiAnnotationParentParentParent.getParent();
            if (mappingsArray == null) {
                return null;
            }
            PsiElement mappingsAnnotationParent = mappingsArray.getParent();
            if (mappingsAnnotationParent == null) {
                return null;
            }
            PsiElement mappingsAnnotationParentParent = mappingsAnnotationParent.getParent();
            if (mappingsAnnotationParentParent instanceof PsiMethod) {
                return (PsiMethod) mappingsAnnotationParentParent;
            }
            return null;

        }
       return null;
    }
}
