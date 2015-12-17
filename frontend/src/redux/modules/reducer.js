import { combineReducers } from 'redux';
import { routerStateReducer } from 'redux-router';

import search from './search';

export default combineReducers({
  router: routerStateReducer,
  search: search
});
