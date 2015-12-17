export default function makeElasticSearchQuery({terms}) {
  return {
    query: {
      match: {
        content: terms || ''
      }
    },
    size: 50,
    highlight: {
      fields: {
        _all: {encoder: 'html'}
      }
    }
  };
}
