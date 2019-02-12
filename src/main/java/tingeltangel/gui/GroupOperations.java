package tingeltangel.gui;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import tingeltangel.core.*;
import tingeltangel.gui.EditorFrame;
import tingeltangel.gui.EditorPanel;
import tingeltangel.gui.IndexListEntry;
import tingeltangel.gui.SearchPanel;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.*;

public class GroupOperations extends JPanel {

    private final static Logger log = LogManager.getLogger(SearchPanel.class);
    Border border = BorderFactory.createTitledBorder("Group operations");
    private JButton generateA4Image = new JButton("Generate A4 Image");
    private JButton addMP3 = new JButton("Add mp3");
    private JButton checkAll = new JButton("Check All");
    EditorFrame mainFrame;
    EditorPanel editorPanel;

    public GroupOperations(final EditorFrame mainFrame, final EditorPanel editorPanel) {
        this.mainFrame = mainFrame;
        this.editorPanel = editorPanel;
        this.setBorder(this.border);
        this.checkAll.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Book book = mainFrame.getBook();
                HashMap<Integer, Entry> indexEntries =  book.getIndexEntries();
                for(Entry i : indexEntries.values()) {
                    book.addSelectedEntry(i);
                }
            }
        });
        this.add(this.checkAll);

        this.generateA4Image.addActionListener(
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        try {
                            int width = 10;
                            int height = 10;
                            int marginVertical_ = 10;
                            int marginHorizontal_ = 10;

                            JTextField marginVertical = new JTextField(Integer.toString(marginVertical_));
                            JTextField marginHorizontal = new JTextField(Integer.toString(marginHorizontal_));
                            JTextField sizeHeight = new JTextField(Integer.toString(height));
                            JTextField sizeWidth = new JTextField(Integer.toString(width));
                            Object[] message = {
                                    "Margin vertical", marginVertical,
                                    "Margin horizontal", marginHorizontal,
                                    "Size - height", sizeHeight,
                                    "Size - width", sizeWidth};
                            int option = JOptionPane.showConfirmDialog(null, message, "Page setup",
                                    JOptionPane.OK_CANCEL_OPTION);
                            if (option == JOptionPane.OK_OPTION) {

                                if (!marginVertical.getText().matches("\\d+")) {
                                    JOptionPane.showMessageDialog(mainFrame, "Format of the \"Margin Vertical\" have to be integer",
                                            "Format of the margin have to be integer", JOptionPane.ERROR_MESSAGE);
                                    throw new NumberFormatException("Incorrect margin format");
                                }

                                if (!marginHorizontal.getText().matches("\\d+")) {
                                    JOptionPane.showMessageDialog(mainFrame, "Format of the margin Horizontal have to be integer",
                                            "Format of the \"Margin Horizontal\" have to be integer", JOptionPane.ERROR_MESSAGE);
                                    throw new NumberFormatException("Incorrect margin format");
                                }

                                if (!sizeHeight.getText().matches("\\d+")) {
                                    JOptionPane.showMessageDialog(mainFrame, "Format of the \"Size Height\" have to be integer",
                                            "Format of the \"Size Height\" have to be integer", JOptionPane.ERROR_MESSAGE);
                                    throw new NumberFormatException("Incorrect size format");
                                }

                                if (!sizeWidth.getText().matches("\\d+")) {
                                    JOptionPane.showMessageDialog(mainFrame, "Format of the \"Size Width\" have to be integer",
                                            "Format of the \"Size Width\" have to be integer", JOptionPane.ERROR_MESSAGE);
                                    throw new NumberFormatException("Incorrect size format");
                                }

                                log.info("Size Height : " + sizeHeight.getText());
                                log.info("Size Width: " + sizeWidth.getText());
                                log.info("Margin Horizontal : " + marginHorizontal.getText());
                                log.info("Margin Vertical : " + marginVertical.getText());




                                JFileChooser fc = new JFileChooser();
                                fc.setFileFilter(new FileNameExtensionFilter("Ting Pattern (*.png)", "png"));
                                if (fc.showSaveDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
                                    try {
                                        String file = fc.getSelectedFile().getCanonicalPath();
                                        if (file.toLowerCase().endsWith(".png")) {
                                            file = file.replaceAll("\\.png$", "");
                                        }
                                        width = Integer.parseInt(sizeWidth.getText());
                                        height = Integer.parseInt(sizeHeight.getText());
                                        marginVertical_ = Integer.parseInt(marginVertical.getText());
                                        marginHorizontal_ = Integer.parseInt(marginHorizontal.getText());
                                        List<Integer> selectedtingCodes = new ArrayList<Integer>();


                                        for(Entry e : mainFrame.getBook().getSelectedEntries()) {
                                            selectedtingCodes.add(Translator.ting2code(e.getTingID()));
                                        }

                                        Codes.drawCodes(selectedtingCodes.toArray(new Integer[selectedtingCodes.size()]),
                                                width, height, marginVertical_, marginHorizontal_, file);
                                    } catch (Exception e) {
                                        JOptionPane.showMessageDialog(mainFrame, "Das Ting Pattern konnte nicht gespeichert werden");
                                        log.error("unable to save code", e);
                                    }
                                }
                            }
                        } catch (NumberFormatException e) {

                        }

                    }
                });
        this.add(this.generateA4Image);
        this.addMP3.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("Select directory from which MP3s will be imported");
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if(fc.showSaveDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
                    try {
                        File dir = new File(fc.getSelectedFile().getPath());
                        File[] files = dir.listFiles(new FilenameFilter() {
                            public boolean accept(File dir, String name) {
                                return name.toLowerCase().endsWith(".mp3");
                            }
                        });
                        ArrayList<File> files_ = new ArrayList<File>(Arrays.asList(files));
                        Collections.sort(files_, new Comparator<File>() {
                            public int compare(File f1, File f2) {
                                return f1.getName().compareTo(f2.getName());
                            }
                        });


                        Book book = mainFrame.getBook();
                        for(File f : files_) {
                            if (!book.isMp3FileInBook(f.getName())) {
                                log.info("Adding mp3 " + f.getName());
                                int nextKey = book.getNextFreeKey();
                                mainFrame.getBook().addEntry(nextKey);

                                Entry entry = mainFrame.getBook().getEntryByTingID(nextKey);

                                entry.setMP3();
                                entry.setMP3(f);
                                String name = f.getName();
                                name = name.replaceAll("\\.mp3$", "");

                                entry.setName(name);
                                String hint = "Precode = %s\n" +
                                        "TYPE = 0\n" +
                                        "[Note] \n" +
                                        "Opa\n" +
                                        "[Content]\n" +
                                        "playoid %s \n" +
                                        "clearver\n" +
                                        "end";
                                hint = String.format(hint, Integer.toString(nextKey), Integer.toString(nextKey));
                                entry.setHint(hint);

                                editorPanel.getList().add(new IndexListEntry(mainFrame.getBook().getEntryByOID(nextKey), editorPanel), 0);
                                new Thread() {
                                    @Override
                                    public void run() {
                                        editorPanel.getList().revalidate();
                                        editorPanel.getList().repaint();
                                    }
                                }.start();

                            }
                        }



                    } catch(Exception e) {
                        JOptionPane.showMessageDialog(mainFrame, "Das Ting Pattern konnte nicht gespeichert werden");
                        System.out.println(e);
                        log.error("unable to save code", e);
                    }
                }
            }
        });
        this.add(this.addMP3);}
}
