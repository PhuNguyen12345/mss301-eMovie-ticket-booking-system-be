import { useState } from "react";
import { Button } from "../../components/ui/Button";
import { Field } from "../../components/ui/Field";
import { ErrorMessage, Loading } from "../../components/ui/Feedback";
import { Pagination } from "../../components/ui/Pagination";
import { useReferences } from "./hooks";
import type { Reference, ReferenceKind } from "./api";

export function ReferencePicker({
  kind,
  add,
  excluded = [],
}: {
  kind: ReferenceKind;
  add: (row: Reference) => void;
  excluded?: number[];
}) {
  const [keyword, setKeyword] = useState("");
  const [filters, setFilters] = useState({ keyword: "", page: 0, size: 10 });
  const [selected, setSelected] = useState("");
  const query = useReferences(kind, filters);
  const search = () => {
    setFilters((previous) => ({
      ...previous,
      keyword: keyword.trim(),
      page: 0,
    }));
    setSelected("");
  };
  const chosen = query.data?.data.find(
    (row) => row.id === Number(selected) && !excluded.includes(row.id),
  );
  const label = kind === "genres" ? "thể loại" : "diễn viên / đạo diễn";
  return (
    <div className="reference-picker">
      <div className="reference-search">
        <Field label={`Tìm ${label}`}>
          <input
            type="search"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                search();
              }
            }}
          />
        </Field>
        <Button onClick={search}>Tìm</Button>
      </div>
      <ErrorMessage
        error={query.error}
        retry={() => {
          void query.refetch();
        }}
      />
      {query.isPending ? (
        <Loading />
      ) : (
        <>
          <div className="reference-search">
            <Field label={`Chọn ${label}`}>
              <select
                value={selected}
                onChange={(e) => setSelected(e.target.value)}
              >
                <option value="">Chọn từ danh mục</option>
                {query.data?.data
                  .filter((row) => !excluded.includes(row.id))
                  .map((row) => (
                    <option key={row.id} value={row.id}>
                      {row.name}
                    </option>
                  ))}
              </select>
            </Field>
            <Button
              disabled={!chosen || query.isError || query.isFetching}
              onClick={() => {
                if (chosen) {
                  add(chosen);
                  setSelected("");
                }
              }}
            >
              Thêm vào phim
            </Button>
          </div>
          {query.data && (
            <Pagination
              page={filters.page}
              totalPages={query.data.totalPages}
              change={(page) => {
                setFilters((previous) => ({ ...previous, page }));
                setSelected("");
              }}
            />
          )}
        </>
      )}
    </div>
  );
}
