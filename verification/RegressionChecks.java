import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.*;
import com.eteks.sweethome3d.swing.*;
import com.eteks.sweethome3d.viewcontroller.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.table.TableColumnModel;

/** Additional checks; the supplied assignment-tests remain unchanged. */
public class RegressionChecks {
  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  public static void main(String[] args) throws Exception {
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    SwingUtilities.invokeAndWait(() -> {
      try {
        verifyPrinting();
      } catch (PrinterException ex) {
        throw new AssertionError(ex);
      }
    });
    System.out.println("PASS: hidden/empty levels, page order, failure restoration, print-only columns and filters");
  }

  private static void verifyPrinting() throws PrinterException {
    Home home = new Home();
    DefaultUserPreferences preferences = new DefaultUserPreferences();
    for (int i = 0; i < 3; i++) {
      Level level = new Level("Level " + i, i * 250, 10, 240);
      home.addLevel(level);
      HomePieceOfFurniture piece = new HomePieceOfFurniture(
          new CatalogPieceOfFurniture("Piece " + i, null, null, 2, 3, 4, true, false));
      home.addPieceOfFurniture(piece);
      piece.setLevel(level);
    }
    home.getLevels().get(1).setViewable(false);
    Level original = home.getLevels().get(2);
    home.setSelectedLevel(original);
    List<Level> visited = new ArrayList<Level>();
    PlanComponent plan = new PlanComponent(home, preferences, null) {
      @Override
      protected List<Selectable> getPaintedItems() {
        visited.add(home.getSelectedLevel());
        return new ArrayList<Selectable>(); // Empty levels must still have a page.
      }
    };
    Graphics2D graphics = new BufferedImage(800, 1000, BufferedImage.TYPE_INT_RGB).createGraphics();
    PageFormat format = new PageFormat();
    try {
      check(plan.print(graphics, format, 1) == Printable.PAGE_EXISTS, "Out-of-order page request");
      check(visited.get(0) == original, "Second viewable level must be Level 2");
      check(plan.print(graphics, format, 0) == Printable.PAGE_EXISTS, "Empty level page");
      check(visited.get(1) == home.getLevels().get(0), "First page must be Level 0");
      check(plan.print(graphics, format, 2) == Printable.NO_SUCH_PAGE, "Hidden level excluded");
      check(plan.print(graphics, format, -1) == Printable.NO_SUCH_PAGE, "Negative page rejected");
      check(home.getSelectedLevel() == original, "Selection restored after all requests");

      final boolean[] failPlan = {true};
      PlanComponent failingPlan = new PlanComponent(home, preferences, null) {
        @Override
        protected List<Selectable> getPaintedItems() {
          if (failPlan[0]) {
            throw new IllegalStateException("Simulated render failure");
          }
          return new ArrayList<Selectable>();
        }
      };
      try {
        failingPlan.print(graphics, format, 0);
        throw new AssertionError("Expected render failure");
      } catch (IllegalStateException expected) {
        check(home.getSelectedLevel() == original, "Selection restored on failure");
      } finally {
        failPlan[0] = false;
      }

      final boolean[] fail = {false};
      FurnitureTable table = new FurnitureTable(home, preferences) {
        @Override
        public Printable getPrintable(PrintMode mode, MessageFormat header, MessageFormat footer) {
          check(getRowCount() == 2, "Printed table includes all and only viewable levels");
          check(getColumnModel().getColumnIndex(HomePieceOfFurniture.SortableProperty.LEVEL) >= 0,
              "Printed table includes Level");
          return (g, f, p) -> {
            if (fail[0]) {
              throw new PrinterException("Simulated table failure");
            }
            return p == 0 ? Printable.PAGE_EXISTS : Printable.NO_SUCH_PAGE;
          };
        }
      };
      FurnitureTable.FurnitureFilter originalFilter = (h, p) -> false;
      table.setFurnitureFilter(originalFilter);
      TableColumnModel columns = table.getColumnModel();
      List<HomePieceOfFurniture.SortableProperty> visible = new ArrayList<>(home.getFurnitureVisibleProperties());
      HomeController controller = new HomeController(home, preferences, new SwingViewFactory() {
        @Override
        public View createFurnitureView(Home h, UserPreferences prefs, FurnitureController c) {
          return table;
        }
      });
      HomePrintableComponent printable = new HomePrintableComponent(home, controller, new Font("Dialog", Font.PLAIN, 12));
      check(printable.print(graphics, format, 0) == Printable.PAGE_EXISTS, "Furniture page exists");
      check(table.getFurnitureFilter() == originalFilter && table.getRowCount() == 0, "UI filter restored");
      fail[0] = true;
      try {
        printable.print(graphics, format, 0);
        throw new AssertionError("Expected table failure");
      } catch (PrinterException expected) {
        check(table.getColumnModel() == columns, "Column model restored on failure");
        check(table.getFurnitureFilter() == originalFilter, "Filter restored on failure");
        check(home.getFurnitureVisibleProperties().equals(visible), "Screen column preferences unchanged");
      }
    } finally {
      graphics.dispose();
    }
  }
}
