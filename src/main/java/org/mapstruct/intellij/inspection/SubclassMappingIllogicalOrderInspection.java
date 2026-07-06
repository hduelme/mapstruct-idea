/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.intellij.inspection;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.codeInsight.AnnotationUtil.findAnnotation;
import static org.mapstruct.intellij.util.MapstructAnnotationUtils.findAllDefinedSubclassMappingAnnotations;
import static org.mapstruct.intellij.util.TargetUtils.getTargetType;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mapstruct.intellij.util.MapstructUtil;

public class SubclassMappingIllogicalOrderInspection extends InspectionBase {

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

            Set<PsiType> seen = new HashSet<>();
            findAllDefinedSubclassMappingAnnotations( method, true )
                    .forEachOrdered( a -> {
                        PsiAnnotationMemberValue source = a.findDeclaredAttributeValue( "source" );
                        if ( !( source instanceof PsiClassObjectAccessExpression sourceClass ) ) {
                            return;
                        }
                        PsiType sourceType = sourceClass.getOperand().getType();
                        if ( seen.contains( sourceType ) ) {
                            // handled in SubclassMappingDoubleSourceSubclassInspection
                            return;
                        }
                        for (PsiType psiType : seen) {
                            if (psiType.isAssignableFrom( sourceType )) {
                                holder.registerProblem( a, "What? Order Wrong" );
                            }
                        }
                        seen.add( sourceType );
                    } );

        }

    }
}
