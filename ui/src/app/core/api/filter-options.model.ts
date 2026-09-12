/**
 * The values the filter controls offer, from `/api/v1/employees/filter-options`.
 *
 * <p>It lives in `core` rather than in the directory feature because two features now read it and
 * neither owns it. The server draws the same line: `DashboardFilters` is deliberately not the
 * directory's query type — what the two share is the vocabulary, not the shape of a request.
 */
export interface DirectoryFilterOptions {
  readonly countries: readonly string[];
  readonly departments: readonly string[];
  readonly jobTitles: readonly string[];
  readonly levels: readonly string[];
}
