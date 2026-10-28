package dev.eclipseline;

import java.util.ArrayList;
import java.util.Iterator;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.*;
import org.eclipse.ui.texteditor.DefaultRangeIndicator;

/** A ruler-only view of annotations. The source viewer and its preferences stay untouched. */
final class RulerModel extends AnnotationModel implements AutoCloseable {
    final IAnnotationModel original;
    private final IAnnotationModelListener changed = ignored -> fireModelChanged(new AnnotationModelEvent(this,true));
    private final QuietDiffer quiet = new QuietDiffer();

    RulerModel(IAnnotationModel original) {
        this.original = original;
        original.addAnnotationModelListener(changed);
    }
    @Override public Iterator<Annotation> getAnnotationIterator() {
        return visible(original.getAnnotationIterator());
    }
    @Override public Iterator<Annotation> getAnnotationIterator(int offset,int length,boolean canStartBefore,boolean canEndAfter) {
        if(original instanceof IAnnotationModelExtension2)
            return visible(((IAnnotationModelExtension2)original).getAnnotationIterator(offset,length,canStartBefore,canEndAfter));
        ArrayList<Annotation> annotations=new ArrayList<>();
        Iterator<Annotation> source=getAnnotationIterator();
        while(source.hasNext()) {
            Annotation annotation=source.next();Position position=original.getPosition(annotation);
            if(position!=null&&position.overlapsWith(offset,length)
                &&(canStartBefore||position.offset>=offset)
                &&(canEndAfter||(long)position.offset+position.length<=(long)offset+length))annotations.add(annotation);
        }
        return annotations.iterator();
    }
    private static Iterator<Annotation> visible(Iterator<Annotation> source) {
        ArrayList<Annotation> annotations=new ArrayList<>();
        while(source.hasNext()) {
            Annotation annotation=source.next();
            if(!(annotation instanceof DefaultRangeIndicator))annotations.add(annotation);
        }
        return annotations.iterator();
    }
    @Override public Position getPosition(Annotation annotation) { return original.getPosition(annotation); }
    @Override public Object getModificationStamp() {
        return original instanceof IAnnotationModelExtension?((IAnnotationModelExtension)original).getModificationStamp():super.getModificationStamp();
    }
    @Override public IAnnotationModel getAnnotationModel(Object key) {
        // An actual empty differ replaces a previously bound differ on older JFace versions.
        // Returning null leaves the old differ attached in STS 3.
        if(IChangeRulerColumn.QUICK_DIFF_MODEL_ID.equals(key))return quiet;
        return original instanceof IAnnotationModelExtension?((IAnnotationModelExtension)original).getAnnotationModel(key):null;
    }
    @Override public void close() { original.removeAnnotationModelListener(changed); }

    private ILineDiffer originalDiffer() {
        IAnnotationModel model=original instanceof IAnnotationModelExtension
            ?((IAnnotationModelExtension)original).getAnnotationModel(IChangeRulerColumn.QUICK_DIFF_MODEL_ID):original;
        return model instanceof ILineDiffer?(ILineDiffer)model:null;
    }
    private final class QuietDiffer extends AnnotationModel implements ILineDiffer {
        @Override public ILineDiffInfo getLineInfo(int line) { return null; }
        @Override public void revertLine(int line)throws org.eclipse.jface.text.BadLocationException { ILineDiffer differ=originalDiffer();if(differ!=null)differ.revertLine(line); }
        @Override public void revertBlock(int line)throws org.eclipse.jface.text.BadLocationException { ILineDiffer differ=originalDiffer();if(differ!=null)differ.revertBlock(line); }
        @Override public void revertSelection(int line,int count)throws org.eclipse.jface.text.BadLocationException { ILineDiffer differ=originalDiffer();if(differ!=null)differ.revertSelection(line,count); }
        @Override public int restoreAfterLine(int line)throws org.eclipse.jface.text.BadLocationException { ILineDiffer differ=originalDiffer();return differ==null?0:differ.restoreAfterLine(line); }
    }
}
