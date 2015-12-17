import React, { Component, PropTypes } from 'react';
import connectData from 'helpers/connectData';
import {connect} from 'react-redux';
import {performSearch} from 'redux/modules/search';

function fetchData(getState, dispatch) {
  return dispatch(performSearch({}));
}

@connectData(fetchData)
@connect((state) => ({
  results: state.search.results,
  totalHits: state.search.totalHits,
  error: state.search.error
}), {performSearch})
export default class Home extends Component {
  static propTypes = {
    results: PropTypes.array,
    totalHits: PropTypes.number,
    error: PropTypes.object,
    performSearch: PropTypes.func.isRequired
  };

  search() {
    const filename = this.refs.searchFilename.value;
    const content = this.refs.searchContent.value;
    return this.props.performSearch({filename, content});
  }

  renderError() {
    const {type, reason, root_cause} = this.props.error;
    return (
      <div>
        <h3>Error</h3>
        <dl>
          <dt>Type</dt><dd>{type}</dd>
          <dt>Reason</dt><dd>{reason}</dd>
          <dt>Root cause</dt>
          <dd>
            <ul>{root_cause.map((cause) =>
              <li>{cause.type}: {cause.reason}</li>)}
            </ul>
          </dd>
        </dl>
      </div>
    );
  }

  render() {
    const styles = require('./Home.scss');
    const resultsRendered = this.props.results.map((result) => {
      const { _source: {slug, filename, content} } = result;
      return (<tr><td className="col-md-1">{slug}</td><td className="col-md-1">{filename}</td><td className="col-md-10">
        <pre>{content}</pre>
      </td></tr>);
    });

    return (
      <div className={styles.home}>
        <div className="container">
          <div className="row">
            <div className="col-md-12">
              <h1>Repoindexer</h1>

              <form>
                <div className="form-group">
                  <label htmlFor="search">File name</label>
                  <input ref="searchFilename" type="text" className="form-control" id="search" onChange={::this.search} placeholder="e.g. *Test*.java" />
                </div>
                <div className="form-group">
                  <label htmlFor="search">Content</label>
                  <input ref="searchContent" type="text" className="form-control" id="search" onChange={::this.search} placeholder="e.g. new HashMap" />
                </div>
              </form>

              {this.props.error ? this.renderError() : (
                <div>
                  <h3>Total hits: {this.props.totalHits}</h3>
                  <table className="table table-striped">
                    <thead>
                    <tr>
                      <th className="col-md-1">Slug</th>
                      <th className="col-md-1">File</th>
                      <th className="col-md-10">Content</th>
                    </tr>
                    </thead>
                    <tbody>
                    {resultsRendered}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    );
  }
}
