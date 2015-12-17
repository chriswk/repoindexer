import makeElasticsearchQuery from '../../helpers/makeElasticsearchQuery';

const SEARCH = 'redux-example/SEARCH';
const SEARCH_SUCCESS = 'redux-example/SEARCH_SUCCESS';
const SEARCH_FAIL = 'redux-example/SEARCH_FAIL';

const initialState = {
  loaded: false,
  results: [],
  error: null,
  totalHits: null
};

export default function search(state = initialState, action = {}) {
  switch (action.type) {
    case SEARCH:
      return {
        ...state,
        loading: true
      };
    case SEARCH_SUCCESS:
      console.log('success', action);
      return {
        ...state,
        loading: false,
        loaded: true,
        results: action.result.hits.hits,
        totalHits: action.result.hits.total,
        error: null
      };
    case SEARCH_FAIL:
      return {
        ...state,
        loading: false,
        loaded: false,
        error: action.error.error
      };
    default:
      return state;
  }
}

export function isLoaded(globalState) {
  return globalState.search && globalState.search.loaded;
}

export function performSearch(terms = '') {
  console.log('perform search', terms);
  const searchOptions = makeElasticsearchQuery({terms});

  return {
    types: [SEARCH, SEARCH_SUCCESS, SEARCH_FAIL],
    promise: (client) => client.post('/_search', {data: searchOptions}).then((res) => {
      console.log('performed search', res);
      return res;
    }, (err) => {
      console.log('search didnt work', err);
      throw err;
    })
  };
}
