/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.intellij.inspection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.CaretState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;
import org.jetbrains.annotations.NotNull;
import org.mapstruct.intellij.MapStructBundle;
import org.mapstruct.intellij.util.MapstructUtil;

import static org.mapstruct.intellij.util.MapstructAnnotationUtils.extractSubclassMappingAnnotations;
import static org.mapstruct.intellij.util.MapstructAnnotationUtils.findAllDefinedSubclassMappingAnnotations;
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
            for ( PsiAnnotation psiAnnotation : method.getAnnotations() ) {
                if ( isSubclassMappingPsiAnnotation( psiAnnotation ) ) {
                    handleSubclassMappingAnnotation( psiAnnotation, problemMap );
                }
                else if ( isSubclassMappingsPsiAnnotation( psiAnnotation ) ) {
                    extractSubclassMappingAnnotations( psiAnnotation )
                            .forEach( p -> handleSubclassMappingAnnotation( p, problemMap ) );
                }
                else {
                    handleAnnotationWithMappingAnnotation( psiAnnotation, problemMap );
                }
            }
            QuickFixFactory quickFixFactory = QuickFixFactory.getInstance();
            for ( Map.Entry<PsiType, List<PsiElement>> problem : problemMap.entrySet() ) {
                List<PsiElement> problemElements = problem.getValue();
                if ( problemElements.size() > 1 ) {
                    for ( PsiElement problemElement : problemElements ) {
                        LocalQuickFix[] quickFixes = getLocalQuickFixes( problemElement, quickFixFactory );
                        holder.registerProblem( problemElement,
                                MapStructBundle.message( "inspection.subclass.mapping.source.subclass.already.defined",
                                problemElement.getText() ), quickFixes );
                    }
                }
            }
        }

        private static void handleSubclassMappingAnnotation( PsiAnnotation psiAnnotation, Map<PsiType,
                List<PsiElement>> problemMap ) {
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
                    } );
        }

        private static @NotNull  LocalQuickFix[] getLocalQuickFixes(PsiElement problemElement,
                                                                    QuickFixFactory quickFixFactory) {
            List<LocalQuickFix> quickFixes = new ArrayList<>(2);
            if ( problemElement instanceof PsiAnnotation ) {
                quickFixes.add( getDeleteFix( problemElement, quickFixFactory ) );
            }
            else if ( problemElement instanceof PsiAnnotationMemberValue problemPsiAnnotationMemberValue ) {
                Optional.ofNullable( problemElement.getParent() ).map( PsiElement::getParent )
                        .map( PsiElement::getParent ).filter( PsiAnnotation.class::isInstance )
                        .ifPresent( annotation -> quickFixes.add(
                                getDeleteFix( annotation, quickFixFactory ) ) );
                quickFixes.add( new ChangeTargetQuickFix( problemPsiAnnotationMemberValue ) );
            }
            return quickFixes.toArray( new LocalQuickFix[]{} );
        }

        private static @NotNull LocalQuickFixAndIntentionActionOnPsiElement getDeleteFix(
                @NotNull PsiElement problemElement, @NotNull QuickFixFactory quickFixFactory) {

            String annotationName = PsiAnnotationImpl.getAnnotationShortName( problemElement.getText() );
            return quickFixFactory.createDeleteFix( problemElement,
                    MapStructBundle.message( "intention.remove.annotation", annotationName ) );
        }

        private static class ChangeTargetQuickFix extends LocalQuickFixOnPsiElement {

            private final String myText;
            private final String myFamilyName;

            private ChangeTargetQuickFix(@NotNull PsiAnnotationMemberValue element) {
                super( element );
                myText = MapStructBundle.message( "intention.change.subclass.mapping.source.property" );
                myFamilyName = MapStructBundle.message( "inspection.subclass.mapping.source.subclass.already.defined",
                        element.getText() );
            }

            @Override
            public @IntentionName @NotNull String getText() {
                return myText;
            }

            @Override
            public void invoke(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull PsiElement psiElement,
                               @NotNull PsiElement psiElement1) {
                FileEditor selectedEditor = FileEditorManager.getInstance( project ).getSelectedEditor();
                if ( selectedEditor instanceof TextEditor textEditor ) {
                    Editor editor = textEditor.getEditor();

                    TextRange textRange = ((PsiClassObjectAccessExpression) psiElement).getOperand().getTextRange();

                    editor.getCaretModel().moveToOffset( textRange.getStartOffset() );
                    LogicalPosition startPosition = editor.getCaretModel().getLogicalPosition();
                    editor.getCaretModel().moveToOffset( textRange.getEndOffset() );
                    editor.getCaretModel().setCaretsAndSelections(
                            Collections.singletonList( new CaretState(startPosition, startPosition,
                                    editor.getCaretModel().getLogicalPosition() ) ) );
                    editor.getScrollingModel().scrollToCaret( ScrollType.MAKE_VISIBLE );
                }
            }

            @Override
            public @IntentionFamilyName @NotNull String getFamilyName() {
                return myFamilyName;
            }

            @Override
            public boolean availableInBatchMode() {
                return false;
            }
        }

    }
}
