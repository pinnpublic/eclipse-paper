package dev.eclipseline;

import java.util.*;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.*;
import org.eclipse.ui.texteditor.DefaultRangeIndicator;

/** Ruler filtering must leave document annotations, preferences and edit actions intact. */
public final class RulerModelTest {
    public static void main(String[] args)throws Exception {
        AnnotationModel source=new AnnotationModel();NativeDiffer diff=new NativeDiffer();
        org.eclipse.jface.text.Document document=new org.eclipse.jface.text.Document("x".repeat(40));source.connect(document);
        source.addAnnotationModel(IChangeRulerColumn.QUICK_DIFF_MODEL_ID,diff);
        Annotation range=new DefaultRangeIndicator(),error=new Annotation("error",false,"error"),breakpoint=new Annotation("breakpoint",false,"breakpoint");
        Position position=new Position(4,2);
        source.addAnnotation(range,new Position(0,20));source.addAnnotation(error,position);source.addAnnotation(breakpoint,new Position(12,1));
        RulerModel clean=new RulerModel(source);
        try {
            List<Annotation> visible=list(clean.getAnnotationIterator());
            if(visible.contains(range)||!visible.contains(error)||!visible.contains(breakpoint)||visible.size()!=2)throw new AssertionError("Native markers must remain; only the range indicator is hidden");
            if(list(source.getAnnotationIterator()).size()!=3||source.getPosition(range)==null)throw new AssertionError("Source model was modified");
            if(clean.getPosition(error)!=position)throw new AssertionError("Native marker position identity changed");
            if(!list(clean.getAnnotationIterator(4,2,true,true)).equals(List.of(error)))throw new AssertionError("Ranged marker iteration lost native geometry");
            IAnnotationModel rulerDiff=clean.getAnnotationModel(IChangeRulerColumn.QUICK_DIFF_MODEL_ID);
            if(!(rulerDiff instanceof ILineDiffer)||rulerDiff==diff||((ILineDiffer)rulerDiff).getLineInfo(4)!=null)throw new AssertionError("Quick Diff must bind a quiet differ, including on old JFace");
            if(source.getAnnotationModel(IChangeRulerColumn.QUICK_DIFF_MODEL_ID)!=diff||!diff.getLineInfo(4).hasChanges())throw new AssertionError("Source Quick Diff was altered");
            ((ILineDiffer)rulerDiff).revertLine(4);if(diff.reverted!=4)throw new AssertionError("Native reversion action lost its source differ");
            int[] changes={0};clean.addAnnotationModelListener(model->{if(model!=clean)throw new AssertionError("Wrong notification model");changes[0]++;});
            changes[0]=0;source.modifyAnnotationPosition(error,new Position(5,2));
            if(changes[0]!=1||clean.getPosition(error).offset!=5)throw new AssertionError("Native marker update was not forwarded");
            clean.close();changes[0]=0;source.removeAnnotation(error);
            if(changes[0]!=0)throw new AssertionError("Original model listener leaked after cleanup");
        } finally {clean.close();source.disconnect(document);}
        System.out.println("PASS: clean ruler hides range and Quick Diff paint, preserves source markers/actions, forwards updates and releases listeners");
    }
    private static List<Annotation> list(Iterator<Annotation> source){List<Annotation> result=new ArrayList<>();source.forEachRemaining(result::add);return result;}
    private static final class NativeDiffer extends AnnotationModel implements ILineDiffer {
        int reverted=-1;
        @Override public ILineDiffInfo getLineInfo(int line){return new ILineDiffInfo(){
            public int getRemovedLinesBelow(){return 1;}public int getRemovedLinesAbove(){return 1;}public int getChangeType(){return CHANGED;}
            public boolean hasChanges(){return true;}public String[] getOriginalText(){return new String[]{"original"};}
        };}
        public void revertLine(int line){reverted=line;}public void revertBlock(int line){reverted=line;}public void revertSelection(int line,int count){reverted=line;}public int restoreAfterLine(int line){return 1;}
    }
}
