/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.intellij.inspection;

import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
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
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.mapstruct.intellij.MapStructBundle;
import org.mapstruct.intellij.util.MapStructVersion;

import static com.intellij.codeInsight.AnnotationUtil.getStringAttributeValue;
import static org.mapstruct.intellij.util.MapstructAnnotationUtils.extractMappingAnnotationsFromMappings;
import static org.mapstruct.intellij.util.MapstructAnnotationUtils.findAllDefinedMappingAnnotations;
import static org.mapstruct.intellij.util.MapstructUtil.MAPPINGS_ANNOTATION_FQN;
import static org.mapstruct.intellij.util.MapstructUtil.MAPPING_ANNOTATION_FQN;

/**
 * @author hduelme
 */
public class TargetPropertyMappedMoreThanOnceInspection extends MoreThanOnceMappedAnnotationInspectionBase<String> {

    @NotNull
    @Override
    protected String getSingleMappingAnnotationFqn() {
        return MAPPING_ANNOTATION_FQN;
    }

    @NotNull
    @Override
    protected String getRepeatableMappingsAnnotationFqn() {
        return MAPPINGS_ANNOTATION_FQN;
    }

    @NotNull
    @Override
    protected String getAttributeName() {
        return "target";
    }

    @NotNull
    @Override
    protected Stream<PsiAnnotation> findAllDefinedMappings(@NotNull PsiModifierListOwner owner,
                                                           @NotNull MapStructVersion mapStructVersion) {
        return findAllDefinedMappingAnnotations( owner, mapStructVersion );
    }

    @Override
    protected Optional<String> extractCompareKeyFromAnnotationMember(
            @NotNull PsiAnnotationMemberValue annotationMemberValue) {
        return Optional.ofNullable( getStringAttributeValue( annotationMemberValue ) )
                .filter( target -> !target.equals( "." ) );
    }

    @Override
    protected LocalQuickFix getChangeTargetQuickFix(@NotNull PsiAnnotationMemberValue problemPsiAnnotationMemberValue) {
        return new ChangeTargetQuickFix( problemPsiAnnotationMemberValue );
    }

    @Override
    protected String getProblemDescription(@NotNull String problemKey) {
        return MapStructBundle.message( "inspection.target.property.mapped.more.than.once", problemKey );
    }

    @NotNull
    @Override
    protected Stream<PsiAnnotation> extractAnnotationsFromRepeatableMappingsAnnotation(
            @NotNull PsiAnnotation mappings) {
        return extractMappingAnnotationsFromMappings( mappings );
    }

    private static class ChangeTargetQuickFix extends LocalQuickFixOnPsiElement {

        private final String myText;
        private final String myFamilyName;

        private ChangeTargetQuickFix(@NotNull PsiAnnotationMemberValue element) {
            super( element );
            myText = MapStructBundle.message( "intention.change.target.property" );
            myFamilyName = MapStructBundle.message( "inspection.target.property.mapped.more.than.once",
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

                TextRange textRange = psiElement.getTextRange();
                String textOfElement = String.valueOf( editor.getDocument()
                        .getCharsSequence()
                        .subSequence( textRange.getStartOffset(), textRange.getEndOffset() ) );
                int targetStart = Strings.indexOf( textOfElement, "\"" ) + 1;
                int targetEnd = textOfElement.lastIndexOf( "\"" );

                editor.getCaretModel().moveToOffset( textRange.getStartOffset() + targetStart );
                LogicalPosition startPosition = editor.getCaretModel().getLogicalPosition();
                editor.getCaretModel().moveToOffset( textRange.getStartOffset() + targetEnd );
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
