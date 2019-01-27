package tingeltangel.gui;

import tingeltangel.core.Book;
import tingeltangel.core.SortedIntList;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PaginationPanel extends JPanel{

    private int pagesNumber;

    private final JButton next = new JButton("next");
    private String[] availablePageSizes = {"10", "25", "50", "100"};
    private JComboBox<String> pageSizes = new JComboBox<>(availablePageSizes);
    private final JButton prev = new JButton("prev");
    private EditorFrame mainFrame;
    private EditorPanel editorPanel;

    public PaginationPanel(final EditorFrame mainFrame, final EditorPanel editorPanel) {
        this.mainFrame = mainFrame;
        this.editorPanel = editorPanel;
        this.add(prev);
        this.add(pageSizes);
        this.add(next);
        this.refresh();

        ActionListener al = new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                Book book = mainFrame.getBook();
                SearchPanel sp = editorPanel.getSearchPanel();
                SortedIntList sortedIntList = sp.getFilterList();
                if (sortedIntList == null) {
                    sortedIntList = book.getIndexIDs();
                }
                int filterSize = sortedIntList.size();
                int pageNumber = book.getPageNumber();
                int pageSize = book.getPageSize();
                pagesNumber = (int) Math.ceil((double) filterSize / (double) pageSize);
                if (e.getSource() == next) {
                    if ((pageNumber + 1 <= pagesNumber)) {
                        pageNumber += 1;
                        book.setPageNumber(pageNumber);
                    }

                } else if (e.getSource() == prev) {
                    pageNumber -=1;
                    if (pageNumber < 1) {
                        pageNumber = 1;
                    }
                    book.setPageNumber(pageNumber);
                } else if(e.getSource() == pageSizes) {
                    book.setPageSize(new Integer(pageSizes.getSelectedItem().toString()));
                    book.setPageNumber(1);
                }
                if (pageNumber == pagesNumber) {
                        next.setEnabled(false);
                    } else {
                        next.setEnabled(true);
                    }
                if (pageNumber == 1) {
                    prev.setEnabled(false);
                    if (pagesNumber == 1) {
                        next.setEnabled(false);
                    }
                } else {
                    prev.setEnabled(true);
                }

                editorPanel.updateList(null);
            }
        };

        this.prev.addActionListener(al);
        this.pageSizes.addActionListener(al);
        this.next.addActionListener(al);

    }

    public void refresh() {
        this.prev.setEnabled(false);
        int pageSize = mainFrame.getBook().getPageSize();
        mainFrame.getBook().setPageNumber(1);
        mainFrame.getBook().setPageSize(10);
        this.pageSizes.setSelectedIndex(0);
        pagesNumber = (int) Math.ceil((double) mainFrame.getBook().getSize()/ (double) pageSize);
        if (pagesNumber > 1) {
            this.next.setEnabled(true);
        } else {
            this.next.setEnabled(false);
        }
    }
}
