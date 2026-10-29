export default function Loading() {
  return (
    <div className="page" aria-busy="true" aria-label="加载中">
      <div className="tabs">
        {[0, 1, 2, 3].map((i) => (
          <span key={i} className="tab skeleton skeleton-tab" />
        ))}
      </div>
      <ul className="template-grid">
        {[0, 1, 2, 3, 4, 5].map((i) => (
          <li key={i}>
            <div className="template-card skeleton" />
          </li>
        ))}
      </ul>
    </div>
  );
}
