export default function makeElasticSearchQuery({filename = '', content = ''}) {
  const filters = [];
  if (filename) {
    filters.push({term: {filename}});
  }
  if (content) {
    filters.push({term: {content}});
  }

  return {
    query: {
      bool: {
        filter: filters
      }
    },
    size: 50,
    timeout: '3s',
    highlight: {
      fields: {
        _all: {encoder: 'html'}
      }
    }
  };
}
