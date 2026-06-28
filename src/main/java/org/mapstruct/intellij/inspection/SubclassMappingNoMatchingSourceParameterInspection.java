package org.mapstruct.intellij.inspection;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.mapstruct.intellij.util.MapstructAnnotationUtils.findAllDefinedSubclassMappingAnnotations;
import static org.mapstruct.intellij.util.MapstructUtil.getSourceParameters;
import static org.mapstruct.intellij.util.TargetUtils.getTargetType;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.mapstruct.intellij.util.MapstructUtil;

public class SubclassMappingNoMatchingSourceParameterInspection extends InspectionBase {

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

            List<PsiType> sourceParameterTypes =  Arrays.stream( getSourceParameters( method ) )
                    .map( PsiParameter::getType ).toList();
            findAllDefinedSubclassMappingAnnotations( method, false )
                    .map( a -> a.findDeclaredAttributeValue( "source" ) )
                    .filter( Objects::nonNull )
                    .filter( source -> {
                        if ( !( source instanceof PsiClassObjectAccessExpression sourceClass ) ) {
                            return false;
                        }
                        PsiType sourceType = sourceClass.getOperand().getType();
                        return  sourceParameterTypes.stream().noneMatch( t -> t.isAssignableFrom( sourceType ) );
                    } )
                    .forEach( subclassMappingWithoutSource ->
                            holder.registerProblem( subclassMappingWithoutSource, "No source" ) );
            // TODO check provided by annotations
        }

    }
}
