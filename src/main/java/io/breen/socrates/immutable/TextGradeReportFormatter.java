package io.breen.socrates.immutable;

import io.breen.socrates.immutable.criteria.Criteria;
import io.breen.socrates.immutable.file.File;
import io.breen.socrates.immutable.submission.*;
import io.breen.socrates.immutable.test.Test;
import io.breen.socrates.model.TestResult;
import io.breen.socrates.model.wrapper.*;

import javax.swing.text.BadLocationException;
import javax.swing.tree.DefaultMutableTreeNode;
import java.io.IOException;
import java.io.Writer;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * A formatter class that formats a grade report into plain text.
 */
public class TextGradeReportFormatter extends GradeReportFormatter {

    private final static SimpleDateFormat dateFmt = new SimpleDateFormat(
            "EEEE, MMMM d, yyyy h:mm:ss a"
    );

    private final static DecimalFormat decFmt = new DecimalFormat("#.#");

    public TextGradeReportFormatter(Criteria criteria) {
        super(criteria);
    }

    protected void format(SubmissionWrapperNode node, Writer w) throws IOException {
        Submission submission = (Submission)node.getUserObject();
        Date now = new Date();

        Map<File, SubmittedFileWrapperNode> map = new HashMap<>(criteria.files.size());
        @SuppressWarnings("unchecked") Enumeration<DefaultMutableTreeNode> children = node
                .children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode n = children.nextElement();

            if (n instanceof SubmittedFileWrapperNode) {
                SubmittedFileWrapperNode sfwn = (SubmittedFileWrapperNode)n;
                map.put(sfwn.matchingFile, sfwn);
            }
        }

        w.append(criteria.assignmentName);
        w.append(" Grade Report");
        line(w);

        w.append("Student: ");
        w.append(submission.studentName);
        line(w);

        w.append("Date: ");
        w.append(dateFmt.format(now));
        line(w);

        line(w);

        double totalPoints = 0.0;
        double earnedPoints = 0.0;
        for (File file : criteria.files) {
            totalPoints += file.pointValue;

            double earnedThisFile = file.pointValue;

            w.append(file.path);
            w.append(" (");
            w.append(decFmt.format(file.pointValue));
            if (file.pointValue == 1) w.append(" point)");
            else w.append(" points)");
            line(w);

            SubmittedFileWrapperNode sfwn = map.get(file);
            if (sfwn == null) {
                w.append("not submitted");
                line(w);
                line(w);
                line(w);
                // don't add to earnedPoints
                continue;
            }

            SubmittedFile submittedFile = (SubmittedFile)sfwn.getUserObject();
            Receipt receipt = submittedFile.receipt;
            if (receipt != null) {
                w.append("submitted on ");
                w.append(dateFmt.format(receipt.getLatestDate()));
                line(w);
            }

            line(w);

            List<Deduction> ds = getDeductions((TestGroupWrapperNode)sfwn.treeModel.getRoot());
            for (Deduction d : ds) {
                double thisDeduction = d.points;

                if (d.points > earnedThisFile) {
                    thisDeduction = earnedThisFile;
                    earnedThisFile = 0.0;
                } else if (d.points == earnedThisFile) {
                    thisDeduction = 0.0;
                    earnedThisFile = 0.0;
                } else {
                    earnedThisFile -= d.points;
                }

                StringBuilder builder = new StringBuilder();
                builder.append("-");
                builder.append(thisDeduction);
                builder.append("\t");
                builder.append(d.description);

                if (d.notes.length() > 0) {
                    builder.append("\n\n\tGrader notes: ");
                    builder.append(d.notes);
                    builder.append("\n");
                }

                w.append(builder.toString());
                line(w);
            }

            line(w);
            line(w);

            earnedPoints += earnedThisFile;
        }

        w.append("total: ");
        w.append(decFmt.format(earnedPoints));
        w.append("/");
        w.append(decFmt.format(totalPoints));
        line(w);

        w.close();
    }

    private void line(Writer writer) throws IOException {
        writer.append("\n");
    }

    private List<Deduction> getDeductions(TestGroupWrapperNode root) {
        List<Deduction> deductions = new LinkedList<>();

        @SuppressWarnings("unchecked") Enumeration<DefaultMutableTreeNode> dfs = root
                .depthFirstEnumeration();
        while (dfs.hasMoreElements()) {
            DefaultMutableTreeNode n = dfs.nextElement();

            if (n instanceof TestGroupWrapperNode) continue;
            if (n instanceof TestWrapperNode) {
                TestWrapperNode node = (TestWrapperNode)n;
                String notes = "";
                try {
                    notes = node.notes.getText(0, node.notes.getLength());
                } catch (BadLocationException ignored) {}

                Test test = (Test)node.getUserObject();
                if (node.getResult() == TestResult.FAILED)
                    deductions.add(new Deduction(test.deduction, test.description, notes));
                if (node.getResult() == TestResult.PASSED && !notes.isEmpty())
                    deductions.add(new Deduction(0, test.description, notes));
            }
        }

        return deductions;
    }

    private class Deduction {

        public final double points;
        public final String description;
        public final String notes;

        public Deduction(double points, String description, String notes) {
            this.points = points;
            this.description = description;
            this.notes = notes;
        }

        public Deduction(double points, String description) {
            this(points, description, "");
        }

        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("-");
            builder.append(points);
            builder.append("\t");
            builder.append(description);

            if (notes.length() > 0) {
                builder.append("\n\n\tGrader notes: ");
                builder.append(notes);
                builder.append("\n");
            }

            return builder.toString();
        }
    }
}
