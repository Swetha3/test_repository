(ns frontend.components.svg
  "Provides a component for placing an SVG image on the page as an <svg> tag,
  so its contents can be styled with the page's CSS.

  Uses SVGInjector to load the referenced SVG image over XHR and insert them
  into the page.

  Some notes on adding SVG images:
  --------------------------------

  SVG assets often come from the designers with unnecessary elements, especially
  `<g>` elements (groups) as an artifact of exporting them from visual editors.
  Since we're going to be interacting with the elements inside them and not
  treating them as black boxes, it's nice to clean them up so that they're
  easier to read. Many of the elements and attributes are metadata which can
  simply be thrown away. The groups are particularly tricky, however, as nested
  groups can each contain transforms which stack. Thus, simply moving the
  contents out of the groups would not result in the same image. Instead, we can
  use Inkscape to create an image with elements in the same place but with fewer
  or no groups.

  (Of course, depending on the application of the image, there may be real
  reasons for grouping certain elements, such as animating portions of an image
  as a unit. Use your judgement.)

  To clean up an SVG:

  1. Open it in Inkscape.
  2. Choose Edit > XML Editor… (Shift-Ctrl-X)
  3. Select the `<g>` which contains everything.
  4. Choose Object > Ungroup (Shift-Ctrl-G)
  5. Repeat steps 3 and 4 until all groups are ungrouped (unless any appear to actually be useful).
  6. Optionally, use the Delete Node button to remove any other unnecessary
     elements (such as `<desc>` and, usually, `<defs>`). You won't be able to clean
     everything out from here, though.
  7. Add appropriate class(es) to the `<svg>` element and elements which should
     be selectable from the page's CSS.
  8. Save the file.
  9. Open in text editor and remove any remaining unnecessary elements and
     attributes (most of the `xmlns`es, generally any `id`s, and probably others),
     as well as colors.
  10. Add a <style> element with minimal style so that the icon is recognizable
      without external CSS. Scope these rules within `root:svg` so they apply only
      when the icon is viewed independently. See Status-Passed.svg for an example.

  IMPORTANT: When editing an SVG used through SVGInjector, remember that the
  SVGs become cached in the browser, and because the SVGs are loaded over XHR,
  not as `<img>`s, force-reloading the page (Shift-Cmd-R or similar) does not
  always empty that cache. To see your changes, you may need to open the SVG
  itself in a separate tab and refresh there."

  (:require [om.core :as om :include-macros true]
            [frontend.utils :refer-macros [html]]))

(defn- svg-inject! [owner]
  (let [node (om/get-node owner)
        {:keys [class]} (om/get-props owner)]
    ;; SVGInjector replaces the img with an svg. It merges the data- attributes
    ;; onto that svg, including data-reactid, so the svg successfully "becomes"
    ;; the component's element. The only hiccup is that it merges the class
    ;; names on the svg element with those on the element it replaces. The first
    ;; time it injects, this is good: we get a mix of the svg's own classes and
    ;; the ones we apply to it. If we re-render injected-svg with a different
    ;; src, though, the result will be the *new* svg's classes plus the *old*
    ;; svg's classes, plus the ones specified in the component props.
    ;;
    ;; To get around that, we forcefully reset the class attribute to the latest
    ;; component version just before we SVGInjector each time, clearing out any
    ;; classes which SVGInjector may have added to the element previously.
    (.setAttribute node "class" class)
    (js/SVGInjector node)))

(defn svg
  "Place an SVG image on the page by URL. Takes a :src for the image and an
  optional :class to apply to the <svg> element."
  [{:keys [class src]} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (svg-inject! owner))
    om/IDidUpdate
    (did-update [_ _ _]
      (svg-inject! owner))
    om/IRender
    (render [_]
      (html
       [:img {:class class :data-src src}]))))
