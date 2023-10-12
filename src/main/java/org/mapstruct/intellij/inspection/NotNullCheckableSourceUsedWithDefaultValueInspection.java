package org.mapstruct.intellij.inspection;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiNameValuePair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NotNullCheckableSourceUsedWithDefaultValueInspection extends MappingAnnotationInspectionBase {

    @Override
    void visitMappingAnnotation( @NotNull ProblemsHolder problemsHolder, @NotNull PsiAnnotation psiAnnotation,
                                 @NotNull MappingAnnotation mappingAnnotation ) {
        // only apply if only one source is used, the user should decide first
        if (mappingAnnotation.getSourceProperty() == null) {
            if (mappingAnnotation.getConstantProperty() != null && mappingAnnotation.getExpressionProperty() == null) {
                checkForNotNullCheckableSource( mappingAnnotation, problemsHolder, psiAnnotation, "Constant value used with " );
            }
            else if (mappingAnnotation.getConstantProperty() == null && mappingAnnotation.getExpressionProperty() != null) {
                checkForNotNullCheckableSource( mappingAnnotation, problemsHolder, psiAnnotation, "Expression used with " );
            }
        }
    }

    private static void checkForNotNullCheckableSource( @NotNull MappingAnnotation mappingAnnotation, @NotNull ProblemsHolder problemsHolder, @NotNull PsiAnnotation psiAnnotation, String x ) {
        List<PsiNameValuePair> defaultSources = new ArrayList<>( 2 );
        if (mappingAnnotation.getDefaultExpressionProperty() != null) {
            defaultSources.add( mappingAnnotation.getDefaultExpressionProperty() );
        }
        if (mappingAnnotation.getDefaultValueProperty() != null) {
            defaultSources.add( mappingAnnotation.getDefaultValueProperty() );
        }
        if (!defaultSources.isEmpty()) {
            problemsHolder.registerProblem( psiAnnotation, x + defaultSources.stream().map( PsiNameValuePair::getAttributeName ).collect( Collectors.joining( " and " ) ) );
        }
    }
}
