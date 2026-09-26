const PageHeader = ({ title, description }) => (
  <header className="mb-10">
    <h1 className="text-4xl font-semibold">{title}</h1>
    <p className="mt-3 max-w-[52ch] text-base vibrant-secondary">
      {description}
    </p>
  </header>
);
export default PageHeader;
