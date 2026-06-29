/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.intellij.inspection;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.mapstruct.intellij.util.MapstructUtil;

import java.util.HashSet;
import java.util.Set;

import static org.mapstruct.intellij.util.MapstructAnnotationUtils.findAllDefinedSubclassMappingAnnotations;
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

            Set<PsiType> seen = new HashSet<>();
            findAllDefinedSubclassMappingAnnotations( method, true )
                    .forEachOrdered( a -> {
                        PsiAnnotationMemberValue source = a.findDeclaredAttributeValue( "source" );
                        if ( !( source instanceof PsiClassObjectAccessExpression sourceClass ) ) {
                            return;
                        }
                        PsiType sourceType = sourceClass.getOperand().getType();
                        if (seen.contains( sourceType )) {
                            holder.registerProblem( a, "Double" );
                            return;
                        }
                        seen.add( sourceType );
                        // Todo mark both! Like
                    } );
        }

    }
}
