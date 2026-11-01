# Standing Rule: Iconography & UI Design Standards

## 1. Strictly No AI-Generated Images for Icons
- **No AI / Raster Icon Generation**: Never generate or use AI-generated images, raster files (.png, .jpg, .webp), or generated graphics for UI icons, menu options, headphone representations, navigation items, or controls anywhere in the application.

## 2. Uniform Outline Vector Iconography
- **Outline Style Only**: All icons representing menu options, navigation tabs, device controls, headphones, battery indicators, settings, and diagnostics must be uniform, cohesive **outline-style vector icons**.
- **Vector Drawables / Compose Icons**: Use Android Vector Drawables (`VectorDrawable` / SVG paths) or standard Material outline vector definitions with consistent stroke width, 24x24dp viewport, optical weight, and color tinting (`LocalContentColor` / theme tokens).
- **Zero Emoji Placeholders**: Never use Unicode emoji strings (such as `🎧`, `⚙️`, `📊`, `ℹ️`) or ad-hoc text glyphs as icon representations.
- **Uniformity Across Screens**: Every icon across all screens, dialogs, quick settings, and navigation bars must follow the identical design system, stroke aesthetic, and outline visual language.
