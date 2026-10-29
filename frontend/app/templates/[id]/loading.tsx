export default function Loading() {
  return (
    <div className="workspace" aria-busy="true" aria-label="加载中">
      <div className="panel create-panel">
        <div className="skeleton" style={{ height: 24, width: 160, borderRadius: 8 }} />
        <div className="cover-frame skeleton" />
      </div>
      <div className="panel board">
        <div className="task-grid">
          {[0, 1, 2].map((i) => (
            <div key={i} className="task-card skeleton" />
          ))}
        </div>
      </div>
    </div>
  );
}
