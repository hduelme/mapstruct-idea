/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.intellij.inspection;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.mapstruct.intellij.util.MapstructUtil;
import org.mapstruct.intellij.util.TargetUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mapstruct.intellij.util.MapstructAnnotationUtils.extractSubclassMappingAnnotations;
import static org.mapstruct.intellij.util.MapstructAnnotationUtils.findAllDefinedSubclassMappingAnnotations;
import static org.mapstruct.intellij.util.MapstructAnnotationUtils.findSubclassMetaAnnotations;
import static org.mapstruct.intellij.util.MapstructAnnotationUtils.isSubclassMappingPsiAnnotation;
import static org.mapstruct.intellij.util.MapstructAnnotationUtils.isSubclassMappingsPsiAnnotation;
import static org.mapstruct.intellij.util.TargetUtils.getTargetType;

public class SubclassMappingDoubleSourceSubclassInspection extends InspectionBase {
    @NotNull
    @Override
    PsiElementVisitor buildVisitorInternal(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new MyJavaElementVisitor( holder );
    }

    private static class MyJavaElementVisitor extends JavaElementVisitor {
        private final ProblemsHolder holder;

        private MyJavaElementVisitor(ProblemsHolder holder) {
            this.holder = holder;
        }

        @Override
        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod( method );

            if ( !MapstructUtil.isMapper( method.getContainingClass() ) ) {
                return;
            }

            PsiType targetType = getTargetType( method );
            if ( targetType == null ) {
                return;
            }
            Map<PsiType, List<PsiElement>> problemMap = new HashMap<>();
            for (PsiAnnotation psiAnnotation : method.getAnnotations()) {
                if (isSubclassMappingPsiAnnotation( psiAnnotation )) {
                    handleSubclassMappingAnnotation( psiAnnotation, problemMap );
                }
                else if (isSubclassMappingsPsiAnnotation( psiAnnotation )) {
                    extractSubclassMappingAnnotations (psiAnnotation).forEach( p -> handleSubclassMappingAnnotation(p, problemMap ) );
                } else {
                    handleAnnotationWithMappingAnnotation(psiAnnotation, problemMap);
                }
            }
            for ( Map.Entry<PsiType, List<PsiElement>> problem : problemMap.entrySet() ) {
                List<PsiElement> problemElements = problem.getValue();
                if (problemElements.size() > 1) {
                    for (PsiElement problemElement : problemElements) {
                        holder.registerProblem( problemElement, problem.getKey() + " Double" );
                    }
                }
            }
        }

        private static void handleSubclassMappingAnnotation( PsiAnnotation psiAnnotation, Map<PsiType, List<PsiElement>> problemMap ) {
            PsiAnnotationMemberValue source = psiAnnotation.findDeclaredAttributeValue( "source" );
            if ( !( source instanceof PsiClassObjectAccessExpression sourceClass ) ) {
                return;
            }
            PsiType sourceType = sourceClass.getOperand().getType();
            problemMap.computeIfAbsent( sourceType, s -> new ArrayList<>() ).add( source );
        }

        private void handleAnnotationWithMappingAnnotation(PsiAnnotation psiAnnotation,
                                                           Map<PsiType, List<PsiElement>> problemMap) {
            PsiClass annotationClass = psiAnnotation.resolveAnnotationType();
            if ( annotationClass == null ) {
                return;
            }
            findAllDefinedSubclassMappingAnnotations( annotationClass, true )
                    .forEach( a -> {
                        PsiAnnotationMemberValue source = a.findDeclaredAttributeValue( "source" );
                        if ( !( source instanceof PsiClassObjectAccessExpression sourceClass ) ) {
                            return;
                        }
                        PsiType sourceType = sourceClass.getOperand().getType();
                        problemMap.computeIfAbsent( sourceType, k -> new ArrayList<>() ).add( psiAnnotation );
                    });
        }

    }
}
