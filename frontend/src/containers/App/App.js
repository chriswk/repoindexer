import React, { Component, PropTypes } from 'react';
import DocumentMeta from 'react-document-meta';
// import { pushState } from 'redux-router';
import config from '../../config';

export default class App extends Component {
  static propTypes = {
    children: PropTypes.object.isRequired
    // pushState: PropTypes.func.isRequired
  };

  static contextTypes = {
    store: PropTypes.object.isRequired
  };

  render() {
    const styles = require('./App.scss');
    return (
      <div className={styles.app}>
        <DocumentMeta {...config.app}/>

        <div className={styles.appContent}>
          {this.props.children}
        </div>
      </div>
    );
  }
}
